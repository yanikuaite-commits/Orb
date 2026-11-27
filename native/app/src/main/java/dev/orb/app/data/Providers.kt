package dev.orb.app.data

const val DEFAULT_GEMINI_TEXT_MODEL = "gemini-3.6-flash"
const val DEFAULT_GROQ_TEXT_MODEL = "llama-3.3-70b-versatile"

val GEMINI_TEXT_MODELS = listOf(
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.8-flash",
    "gemini-3.1-flash-lite",
    "gemini-flash-latest",
)

val GROQ_TEXT_MODELS = listOf(
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "openai/gpt-oss-120b",
    "openai/gpt-oss-20b",
    "qwen/qwen3-32b",
    "meta-llama/llama-4-scout-17b-16e-instruct",
    "meta-llama/llama-4-maverick-17b-128e-instruct",
)
