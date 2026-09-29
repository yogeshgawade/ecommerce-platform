import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    mongodb_uri: str = os.getenv(
        "MONGODB_URI",
        "mongodb://admin:mongo_dev_password@localhost:27017/wishlist_db?authSource=admin&directConnection=true",
    )
    catalog_service_url: str = os.getenv("CATALOG_SERVICE_URL", "http://localhost:8081").rstrip("/")
    app_jwt_secret: str = os.getenv(
        "APP_JWT_SECRET",
        "local-development-secret-change-this-to-a-long-random-value",
    )
    max_page_size: int = int(os.getenv("WISHLIST_MAX_PAGE_SIZE", "100"))


settings = Settings()
