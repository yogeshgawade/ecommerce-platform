import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from sqlalchemy import text

from .database import SessionLocal
from .email_sender import build_email_sender
from .kafka_runtime import KafkaRuntime
from .processor import NotificationProcessor

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

processor = NotificationProcessor(SessionLocal, build_email_sender())
kafka_runtime = KafkaRuntime(processor)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    kafka_runtime.start()
    yield
    kafka_runtime.stop()


app = FastAPI(title="Notification Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    try:
        with SessionLocal() as session:
            session.execute(text("select 1"))
    except Exception as exc:
        logger.exception("Notification database health check failed")
        raise HTTPException(status_code=503, detail="Notification database is unavailable") from exc
    return {"status": "UP"}
