"""AWS Lambda Function URL entry point; the ordinary Uvicorn app remains supported."""
import logging
import os
import asyncio

# Lambda is always production. Set before importing the application.
os.environ['COMPANION_ENV'] = 'production'

from mangum import Mangum
from .app import app

# Do not emit per-request access records. AWS still records platform metadata.
logging.getLogger('mangum.http').setLevel(logging.WARNING)


def handler(event, context):
    # No asynchronous resources are shared between invocations. Own the loop explicitly
    # rather than depending on Python's implicit-loop behavior across runtime versions.
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        return Mangum(app, lifespan='off')(event, context)
    finally:
        loop.close()
        asyncio.set_event_loop(None)
