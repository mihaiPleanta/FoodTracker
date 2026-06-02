import pytest

from services.ollama_client import OllamaClient, OllamaUnavailable


class _BoomClient:
    async def chat(self, **kwargs):
        raise ConnectionError("connection refused")


class _OkClient:
    def __init__(self):
        self.kwargs = None

    async def chat(self, **kwargs):
        self.kwargs = kwargs
        return {"message": {"content": '{"ok": true}'}}


async def test_generate_json_wraps_connection_errors():
    c = OllamaClient()
    c._client = _BoomClient()
    with pytest.raises(OllamaUnavailable):
        await c.generate_json([{"role": "user", "content": "hi"}])


async def test_generate_json_returns_message_content():
    c = OllamaClient()
    ok = _OkClient()
    c._client = ok
    out = await c.generate_json([{"role": "user", "content": "hi"}])
    assert out == '{"ok": true}'
    assert ok.kwargs["format"] == "json"
