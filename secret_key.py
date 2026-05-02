import pickle
from cryptography.fernet import Fernet
import os
from dotenv import load_dotenv

load_dotenv()

key = os.getenv("SECRET_KEY")
if not key:
    raise ValueError("SECRET_KEY missing in .env")

cipher = Fernet(key.encode())

with open("src/data.enc", "rb") as f:
    decrypted = cipher.decrypt(f.read())

self.model = pickle.loads(decrypted)