import sys
import os
import joblib
import pandas as pd
import uuid
from datetime import datetime
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
from reportlab.lib.styles import getSampleStyleSheet
from PyQt5.QtWidgets import QSizePolicy
from PyQt5.QtWidgets import (
    QApplication, QWidget, QLabel, QLineEdit,
    QPushButton, QVBoxLayout, QHBoxLayout,
    QComboBox, QGridLayout, QMessageBox,
    QScrollArea, QStackedWidget, QFrame
)
from PyQt5.QtCore import Qt

# ================= SAFE PATH HANDLING =================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

model_path = os.path.join(BASE_DIR, "src", "data.pkl")
user_data_dir = os.path.join(BASE_DIR, "User_data")
os.makedirs(user_data_dir, exist_ok=True)

model = joblib.load(model_path)

feature_names = [
    "Age","Gender","Spicy_Food","Tobacco","Alcohol",
    "Sleep_Hours","Stress","Skip_Meals",
    "Soft_Drinks","Empty_Stomach_Pain",
    "Day1_pH_Before","Day1_pH_After",
    "Day2_pH_Before","Day2_pH_After"
]

# ================= LOGIN PAGE =================
class LoginPage(QWidget):
    def __init__(self, stacked):
        super().__init__()
        self.stacked = stacked
        self.setStyleSheet(self.style())

        layout = QVBoxLayout()
        layout.setAlignment(Qt.AlignCenter)
        layout.setSpacing(20)

        title = QLabel("🩺 Ulcer AI Clinical Login")
        title.setStyleSheet("font-size:28px; font-weight:bold;")
        title.setAlignment(Qt.AlignCenter)
        layout.addWidget(title)

        self.user = QLineEdit()
        self.user.setPlaceholderText("Username")
        layout.addWidget(self.user)

        self.pwd = QLineEdit()
        self.pwd.setPlaceholderText("Password")
        self.pwd.setEchoMode(QLineEdit.Password)
        layout.addWidget(self.pwd)

        btn = QPushButton("Login")
        btn.setMinimumHeight(40)
        btn.clicked.connect(self.login)
        layout.addWidget(btn)

        self.setLayout(layout)

    def login(self):
        if self.user.text() == "admin" and self.pwd.text() == "1234":
            self.stacked.setCurrentIndex(1)
        else:
            QMessageBox.warning(self,"Error","Invalid Credentials")

    def style(self):
        return """
        QWidget {background:#0f172a; color:white; font-family:Segoe UI;}
        QLineEdit {
            padding:10px;
            border-radius:10px;
            background:white;
            color:black;
        }
        QPushButton {
            background:#3b82f6;
            color:white;
            border-radius:10px;
        }
        QPushButton:hover {background:#2563eb;}
        """

# ================= MAIN APPLICATION =================
class UlcerAI(QWidget):
    def __init__(self):
        super().__init__()

        self.setMinimumSize(1000, 700)
        self.patient_id = "PAT-" + str(uuid.uuid4())[:8].upper()
        self.latest_data = None
        self.latest_text = ""

        self.setStyleSheet(self.style())

        main_layout = QVBoxLayout(self)
        main_layout.setContentsMargins(20,20,20,20)
        main_layout.setSpacing(15)

        header = QLabel("Saliva-Based Ulcer Risk Assessment System")
        header.setAlignment(Qt.AlignCenter)
        header.setStyleSheet("font-size:24px; font-weight:bold;")
        main_layout.addWidget(header)

        id_label = QLabel(f"Patient ID: {self.patient_id}")
        id_label.setAlignment(Qt.AlignCenter)
        id_label.setStyleSheet("color:#64748b;")
        main_layout.addWidget(id_label)

        content_layout = QHBoxLayout()
        content_layout.setSpacing(20)
        main_layout.addLayout(content_layout, stretch=1)

        # -------- LEFT FORM --------
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)

        form_container = QWidget()
        form_layout = QGridLayout(form_container)
        form_layout.setSpacing(12)

        self.inputs = {}

        combos = {
            "Gender":["Male","Female"],
            "Spicy_Food":["Yes","No"],
            "Tobacco":["Yes","No"],
            "Alcohol":["Yes","No"],
            "Skip_Meals":["Yes","No"],
            "Soft_Drinks":["Yes","No"],
            "Empty_Stomach_Pain":["Yes","No"]
        }

        for row, name in enumerate(feature_names):
            label = QLabel(name.replace("_"," "))
            form_layout.addWidget(label, row, 0)

            if name in combos:
                widget = QComboBox()
                widget.addItems(combos[name])
            else:
                widget = QLineEdit()

            widget.setMinimumHeight(30)
            form_layout.addWidget(widget, row, 1)
            self.inputs[name] = widget

        scroll.setWidget(form_container)
        content_layout.addWidget(scroll, stretch=3)

        # -------- RIGHT RESULT PANEL --------
        self.result_frame = QFrame()
        self.result_frame.setStyleSheet(
            "background:#0f172a; border-radius:15px; padding:20px;"
        )

        result_layout = QVBoxLayout(self.result_frame)

        self.result_label = QLabel("Result will appear here...")
        self.result_label.setWordWrap(True)
        self.result_label.setAlignment(Qt.AlignTop)
        self.result_label.setStyleSheet("color:white; font-size:15px;")

        result_layout.addWidget(self.result_label)
        content_layout.addWidget(self.result_frame, stretch=2)

        # -------- BUTTONS --------
        button_layout = QHBoxLayout()
        button_layout.setSpacing(20)

        btn_predict = QPushButton("Analyze")
        btn_pdf = QPushButton("Save PDF")
        btn_export = QPushButton("Export History")

        btn_predict.clicked.connect(self.predict)
        btn_pdf.clicked.connect(self.save_pdf)
        btn_export.clicked.connect(self.export_history)

        for btn in [btn_predict, btn_pdf, btn_export]:
            btn.setMinimumHeight(45)
            btn.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
            button_layout.addWidget(btn)

        main_layout.addLayout(button_layout)

    # ================= INPUT COLLECTION =================
    def collect_input(self):
        values = []
        for name in feature_names:
            widget = self.inputs[name]
            if isinstance(widget, QComboBox):
                values.append(1 if widget.currentText() in ["Male","Yes"] else 0)
            else:
                values.append(float(widget.text()))
        return pd.DataFrame([values], columns=feature_names)

    # ================= PREDICTION =================
    def predict(self):
        try:
            df = self.collect_input()
            self.latest_data = df

            prob = model.predict_proba(df)[0][1]

            if prob > 0.7:
                risk = "HIGH RISK"
                color = "#ef4444"
            elif prob > 0.3:
                risk = "MODERATE RISK"
                color = "#f59e0b"
            else:
                risk = "LOW RISK"
                color = "#22c55e"

            result_text = f"""
Patient ID: {self.patient_id}

Risk Probability: {prob*100:.2f}%
Risk Level: {risk}

Recommendations:
• Maintain balanced diet
• Avoid spicy & tobacco
• Improve sleep quality
• Reduce stress levels
"""

            self.latest_text = result_text
            self.result_label.setStyleSheet(
                f"color:{color}; font-size:16px;"
            )
            self.result_label.setText(result_text)

        except:
            QMessageBox.warning(self,"Error","Please enter valid numeric inputs.")

    # ================= SAVE PDF =================
    def save_pdf(self):
        if not self.latest_text:
            QMessageBox.warning(self,"Error","Run prediction first.")
            return

        filename = os.path.join(
            user_data_dir,
            f"{self.patient_id}_Report.pdf"
        )

        doc = SimpleDocTemplate(filename)
        styles = getSampleStyleSheet()
        story = []

        story.append(Paragraph("Ulcer AI Clinical Report", styles['Title']))
        story.append(Spacer(1,12))
        story.append(Paragraph(self.latest_text.replace("\n","<br/>"), styles['Normal']))
        doc.build(story)

        QMessageBox.information(self,"Saved",f"Report saved successfully.")

    # ================= EXPORT HISTORY =================
    def export_history(self):
        if self.latest_data is None:
            QMessageBox.warning(self,"Error","Run prediction first.")
            return

        df = self.latest_data.copy()
        df["Patient_ID"] = self.patient_id
        df["Timestamp"] = datetime.now()

        history_path = os.path.join(user_data_dir, "patient_history.csv")

        df.to_csv(
            history_path,
            mode='a',
            header=not os.path.exists(history_path),
            index=False
        )

        QMessageBox.information(self,"Exported","Patient data saved.")

    def style(self):
        return """
        QWidget {background:#f8fafc; font-family:Segoe UI;}
        QLabel {font-size:14px;}
        QLineEdit, QComboBox {
            padding:6px;
            border-radius:8px;
            border:1px solid #cbd5e1;
            background:white;
        }
        QPushButton {
            background:#2563eb;
            color:white;
            padding:8px;
            border-radius:10px;
        }
        QPushButton:hover {background:#1d4ed8;}
        """

# ================= START APPLICATION =================
app = QApplication(sys.argv)

stacked = QStackedWidget()
login = LoginPage(stacked)
main = UlcerAI()

stacked.addWidget(login)
stacked.addWidget(main)

stacked.resize(1100, 750)
stacked.show()

sys.exit(app.exec_())