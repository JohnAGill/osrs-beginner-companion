"""Bound request memory and prevent shared caches from storing character responses."""
from .models import ErrorResponse
from starlette.responses import JSONResponse

MAX_REQUEST_BYTES = 128 * 1024


class RequestLimits:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope['type'] != 'http':
            return await self.app(scope, receive, send)

        async def private_send(message):
            if message['type'] == 'http.response.start':
                message['headers'] = [(key, value) for key, value in message.get('headers', [])
                                      if key.lower() != b'cache-control'] + [(b'cache-control', b'no-store')]
            await send(message)

        async def reject():
            response = JSONResponse(status_code=413, content=ErrorResponse(
                error={'code': 'request_too_large', 'message': 'Request exceeds the service size limit.'}).model_dump())
            await response(scope, receive, private_send)

        headers = dict(scope.get('headers', []))
        try:
            declared = int(headers.get(b'content-length', b'0'))
        except ValueError:
            declared = MAX_REQUEST_BYTES + 1
        if declared < 0 or declared > MAX_REQUEST_BYTES:
            return await reject()
        chunks, size = [], 0
        while True:
            message = await receive()
            if message['type'] == 'http.disconnect':
                return
            chunk = message.get('body', b'')
            size += len(chunk)
            if size > MAX_REQUEST_BYTES:
                return await reject()
            chunks.append(chunk)
            if not message.get('more_body', False):
                break
        delivered = False

        async def replay():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {'type': 'http.request', 'body': b''.join(chunks), 'more_body': False}
            return await receive()

        await self.app(scope, replay, private_send)
