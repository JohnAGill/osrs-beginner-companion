FROM python:3.12-slim@sha256:a0f57064cbdcd6285fa45f51237ab0b35ea8ebaed06a1f5973e89e19dfb86704
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 COMPANION_ENV=production
WORKDIR /app
COPY deploy/requirements.txt /app/deploy/requirements.txt
RUN pip install --no-cache-dir -r deploy/requirements.txt
COPY pyproject.toml README.md LICENSE THIRD_PARTY_NOTICES.md /app/
COPY LICENSES /app/LICENSES
COPY beginner_companion /app/beginner_companion
RUN pip install --no-cache-dir --no-deps . && pip check && useradd --uid 10001 --create-home companion
USER 10001
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/v1/health', timeout=2)"
CMD ["python", "-m", "uvicorn", "beginner_companion.api.app:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log", "--no-proxy-headers", "--limit-concurrency", "64", "--timeout-keep-alive", "5"]
