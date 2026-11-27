"""The conversation loop: history, tools, memory and the model, in one place."""
from __future__ import annotations

from typing import Any

from google.genai import types

from . import llm, memory, prompts, settings, tools

MAX_TOOL_ROUNDS = 8
HISTORY_TURNS = 16


class Agent:
    def __init__(self) -> None:
        self.memory = memory.MemoryStore()
        self.history = memory.HistoryStore()

    def _contents(self, user_text: str, audio: tuple[bytes, str] | None) -> list[types.Content]:
        contents: list[types.Content] = []
        for message in self.history.messages(HISTORY_TURNS * 2):
            role = "model" if message.get("role") == "assistant" else "user"
            text_value = str(message.get("text") or "").strip()
            if text_value:
                contents.append(types.Content(role=role, parts=[types.Part(text=text_value)]))

        parts: list[types.Part] = []
        if audio:
            data, mime = audio
            parts.append(types.Part.from_bytes(data=data, mime_type=mime))
        parts.append(types.Part(text=user_text or "Ouça o áudio e responda."))
        contents.append(types.Content(role="user", parts=parts))
        return contents

    def _config(self) -> types.GenerateContentConfig:
        return types.GenerateContentConfig(
            system_instruction=prompts.system_prompt(self.memory.summary()),
            temperature=0.7,
            tools=[types.Tool(function_declarations=tools.DECLARATIONS)],
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
        )

    def chat(self, message: str, audio: tuple[bytes, str] | None = None) -> dict[str, Any]:
        if not settings.api_key():
            return {
                "reply": (
                    "Ainda não tenho uma chave da API Gemini configurada. "
                    "Abra os ajustes e cole sua chave para eu começar a funcionar."
                ),
                "tools": [],
                "ok": False,
                "error": "missing_api_key",
            }

        contents = self._contents(message, audio)
        trace: list[dict[str, Any]] = []
        reply = ""

        for _ in range(MAX_TOOL_ROUNDS):
            response = llm.call(contents, tier=llm.SMART, config=self._config())
            if response is None:
                if llm.last_error() == "key":
                    return {
                        "reply": (
                            "Sua chave da API foi recusada. Confira a chave em "
                            "Ajustes — ela precisa ser uma chave válida do Google AI Studio."
                        ),
                        "tools": trace,
                        "ok": False,
                        "error": "invalid_api_key",
                    }
                detail = llm.last_detail()
                return {
                    "reply": (
                        "Não consegui falar com o modelo agora"
                        + (f" ({detail})" if detail else "")
                        + ". Pode ser cota, modelo indisponível ou falta de conexão. "
                        "Tente de novo em alguns instantes."
                    ),
                    "tools": trace,
                    "ok": False,
                    "error": "model_unavailable",
                }

            calls = list(getattr(response, "function_calls", None) or [])
            if not calls:
                reply = llm.response_text(response)
                break

            model_content = response.candidates[0].content if response.candidates else None
            if model_content is not None:
                contents.append(model_content)

            response_parts: list[types.Part] = []
            for call in calls:
                arguments = dict(call.args or {})
                result = tools.run(call.name or "", arguments)
                trace.append({"name": call.name, "arguments": arguments, "result": result})
                response_parts.append(
                    types.Part.from_function_response(
                        name=call.name or "unknown", response={"result": result}
                    )
                )
            contents.append(types.Content(role="user", parts=response_parts))
        else:
            reply = "Fiz várias consultas mas não consegui fechar a resposta. Pode reformular?"

        if not reply:
            reply = "Não consegui produzir uma resposta desta vez."

        self.history.append("user", message)
        self.history.append("assistant", reply)
        return {"reply": reply, "tools": trace, "ok": True}

    def transcribe(self, audio: bytes, mime: str = "audio/wav") -> str:
        return llm.transcribe(audio, mime_type=mime, language=str(settings.get("language") or ""))
