FROM python:3.12-slim

WORKDIR /app

Copy . .

RUN pip install -r requirements.txt

EXPOSE 8080

CMD ["python", "main.py"]
