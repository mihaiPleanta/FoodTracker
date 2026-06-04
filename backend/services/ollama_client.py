from __future__ import annotations

import os
import random

from ollama import AsyncClient

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma3:4b")


class OllamaUnavailable(Exception):
    """Raised when Ollama can't be reached or times out."""


class OllamaClient:
    def __init__(
        self,
        host: str = OLLAMA_BASE_URL,
        model: str = OLLAMA_MODEL,
        timeout: float = 120.0,
    ) -> None:
        self._client = AsyncClient(host=host, timeout=timeout)
        self._model = model

    async def generate_json(self, messages: list[dict]) -> str:
        """Call chat in JSON mode. Returns the raw content string (caller
        parses/validates with Pydantic). `format="json"` is supported across all
        ollama-lib/server versions. Wraps transport errors as OllamaUnavailable."""
        try:
            response = await self._client.chat(
                model=self._model,
                messages=messages,
                format="json",
                options={"temperature": 0.9, "seed": random.randint(0, 2_147_483_647)},
            )
        except Exception as exc:  # connection refused, timeout, etc.
            raise OllamaUnavailable(str(exc)) from exc
        return response["message"]["content"]


_singleton: OllamaClient | None = None


def get_ollama_client() -> OllamaClient:
    """FastAPI dependency. Used as Depends(get_ollama_client) — overridden in
    tests via app.dependency_overrides."""
    global _singleton
    if _singleton is None:
        _singleton = OllamaClient()
    return _singleton
