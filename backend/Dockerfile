FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY service-account.json .
COPY . .
EXPOSE 8080
CMD uvicorn api:app --host 0.0.0.0 --port $PORT