import sys, os
import joblib
import pandas as pd
import uuid
from datetime import datetime

from PyQt5.QtWidgets import *
from PyQt5.QtGui import QIcon
from PyQt5.QtCore import Qt

from updater import check_update

# ================= SAFE PATH =================
if getattr(sys, 'frozen', False):
    BASE_DIR = sys._MEIPASS
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))

model_path = os.path.join(BASE_DIR, "src", "data.pkl")
user_data_dir = os.path.join(BASE_DIR, "User_data")
os.makedirs(user_data_dir, exist_ok=True)

model = joblib.load(model_path)

CURRENT_VERSION = "v1.0.0"

feature_names = [
    "Age","Gender","Spicy_Food","Tobacco","Alcohol",
    "Sleep_Hours","Stress","Skip_Meals",
    "Soft_Drinks","Empty_Stomach_Pain",
    "Day1_pH_Before","Day1_pH_After",
    "Day2_pH_Before","Day2_pH_After"
]

# ================= UPDATE DIALOG =================
class UpdateDialog(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Updating...")
        self.setFixedSize(300, 120)

        layout = QVBoxLayout(self)

        self.label = QLabel("Checking for updates...")
        self.bar = QProgressBar()

        layout.addWidget(self.label)
        layout.addWidget(self.bar)

    def set_progress(self, value):
        self.bar.setValue(value)

    def set_status(self, text):
        self.label.setText(text)

# ================= LOGIN =================
class LoginPage(QWidget):
    def __init__(self, stacked):
        super().__init__()
        self.stacked = stacked
        self.setStyleSheet("background:#0f172a; color:white;")

        layout = QVBoxLayout(self)

        title = QLabel("Ulcer Risk AI")
        title.setAlignment(Qt.AlignCenter)
        title.setStyleSheet("font-size:28px; font-weight:bold;")
        layout.addWidget(title)

        self.user = QLineEdit()
        self.user.setPlaceholderText("Username")

        self.pwd = QLineEdit()
        self.pwd.setPlaceholderText("Password")
        self.pwd.setEchoMode(QLineEdit.Password)

        btn = QPushButton("Login")
        btn.clicked.connect(self.login)

        for w in [self.user, self.pwd, btn]:
            layout.addWidget(w)

    def login(self):
        if self.user.text() == "admin" and self.pwd.text() == "admin@123":
            self.stacked.setCurrentIndex(1)
        else:
            QMessageBox.warning(self,"Error","Invalid Credentials")

# ================= MAIN APP =================
class UlcerAI(QWidget):
    def __init__(self):
        super().__init__()

        self.setMinimumSize(1000,700)
        self.patient_id = "PAT-" + str(uuid.uuid4())[:8].upper()

        layout = QVBoxLayout(self)

        title = QLabel("Ulcer Risk AI System")
        title.setAlignment(Qt.AlignCenter)
        title.setStyleSheet("font-size:20px; font-weight:bold;")
        layout.addWidget(title)

        # VERSION DISPLAY
        version_label = QLabel(f"Version: {CURRENT_VERSION}")
        version_label.setAlignment(Qt.AlignCenter)
        layout.addWidget(version_label)

        self.inputs = {}
        grid = QGridLayout()

        combos = {
            "Gender":["Male","Female"],
            "Spicy_Food":["Yes","No"],
            "Tobacco":["Yes","No"],
            "Alcohol":["Yes","No"],
            "Skip_Meals":["Yes","No"],
            "Soft_Drinks":["Yes","No"],
            "Empty_Stomach_Pain":["Yes","No"]
        }

        for i, name in enumerate(feature_names):
            label = QLabel(name.replace("_"," "))

            if name in combos:
                inp = QComboBox()
                inp.addItems(combos[name])
            else:
                inp = QLineEdit()

            grid.addWidget(label, i, 0)
            grid.addWidget(inp, i, 1)
            self.inputs[name] = inp

        layout.addLayout(grid)

        self.result = QLabel("Result will appear here")
        layout.addWidget(self.result)

        btn = QPushButton("Analyze")
        btn.clicked.connect(self.predict)
        layout.addWidget(btn)

    def collect(self):
        values = []
        for name in feature_names:
            w = self.inputs[name]

            if isinstance(w, QComboBox):
                values.append(1 if w.currentText() in ["Male","Yes"] else 0)
            else:
                values.append(float(w.text()))

        return pd.DataFrame([values], columns=feature_names)

    def predict(self):
        try:
            df = self.collect()
            prob = model.predict_proba(df)[0][1]

            if prob > 0.7:
                risk = "HIGH"
            elif prob > 0.3:
                risk = "MEDIUM"
            else:
                risk = "LOW"

            self.result.setText(f"Risk: {risk} ({prob*100:.2f}%)")

        except:
            QMessageBox.warning(self,"Error","Invalid Input")

# ================= RUN =================
app = QApplication(sys.argv)

# ICON
app.setWindowIcon(QIcon(os.path.join(BASE_DIR, "assets", "app.ico")))

# UPDATE CHECK WITH UI
dialog = UpdateDialog()
dialog.show()

def progress(val):
    dialog.set_progress(val)

def status(txt):
    dialog.set_status(txt)

check_update(progress, status)

dialog.close()

# MAIN WINDOW
stack = QStackedWidget()
login = LoginPage(stack)
main = UlcerAI()

stack.addWidget(login)
stack.addWidget(main)

stack.resize(1000,700)
stack.show()

sys.exit(app.exec_())