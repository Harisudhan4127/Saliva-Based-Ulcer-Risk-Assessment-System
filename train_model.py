import sys
import os
import subprocess
import pandas as pd
import joblib
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier

print("Training model...")

# 📁 Base project directory
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# 📂 Paths
data_path = os.path.join(BASE_DIR, "src", "data.csv")
model_path = os.path.join(BASE_DIR, "src", "data.pkl")

# Load dataset
df = pd.read_csv(data_path)
df.columns = df.columns.str.strip()

# Convert categorical columns
df["Gender"] = df["Gender"].map({"Male": 1, "Female": 0})

yes_no_cols = [
    "Spicy_Food","Tobacco","Alcohol",
    "Skip_Meals","Soft_Drinks","Empty_Stomach_Pain"
]

for col in yes_no_cols:
    df[col] = df[col].map({"Yes": 1, "No": 0})

# Features and Target
X = df.drop("Ulcer", axis=1)
y = df["Ulcer"]

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

# Train model
model = RandomForestClassifier(n_estimators=100, random_state=42)
model.fit(X_train, y_train)

# Save model
joblib.dump(model, model_path)

print("Model saved successfully!")

# 🚀 Launch GUI
print("Launching GUI...")
subprocess.Popen([sys.executable, os.path.join(BASE_DIR, "main.py")])