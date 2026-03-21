# 🧬 Saliva-Based Ulcer Risk Assessment System  

![Platform](https://img.shields.io/badge/Platform-Python%20%7C%20Web-blue)  ![Language](https://img.shields.io/badge/Language-Python-orange)  ![Domain](https://img.shields.io/badge/Domain-Healthcare%20AI-green)  ![Algorithm](https://img.shields.io/badge/Algorithm-SWBRA-red)  ![Status](https://img.shields.io/badge/Project-Academic%20Research-purple)

---

## 📌 Abstract

The **Saliva-Based Ulcer Risk Assessment System (SURAS)** is an AI-driven healthcare analytics platform designed to predict the risk of oral ulcers using salivary biomarker analysis. The system processes biochemical indicators such as inflammatory cytokines, salivary pH, immunoglobulins, and protein markers to determine ulcer susceptibility.

A structured predictive model named **Saliva Weighted Biomarker Risk Algorithm (SWBRA)** is implemented to classify risk levels into Low, Moderate, and High categories. The system supports early detection, preventive healthcare planning, and biomedical research in non-invasive diagnostic technologies.

---

# 1️⃣ Introduction

Oral ulcers are inflammatory lesions that affect the oral mucosa, causing pain and discomfort. Traditional diagnosis often occurs after visible symptom onset.

Saliva contains diagnostic biomarkers that reflect immune response and inflammation levels. By applying artificial intelligence techniques to biomarker datasets, early risk detection becomes possible.

This project aims to:

- Analyze salivary biomarker datasets  
- Apply preprocessing and normalization  
- Train predictive machine learning models  
- Generate an interpretable ulcer risk score  
- Provide a clinician-friendly reporting interface  

---

# 2️⃣ Domain and Sub-Domain

**Domain:** Healthcare Artificial Intelligence  

**Sub-Domain:** Biomedical Data Analytics & Predictive Diagnostics  

---

# 3️⃣ System Architecture

## 3.1 Data Input Layer

- Salivary biomarker dataset (CSV format)
- Inflammatory markers (e.g., IL-6, CRP)
- Salivary pH values
- Immunoglobulin levels (IgA)
- Total protein concentration
- Optional patient metadata

---

## 3.2 Data Processing Layer

1. Data cleaning and validation  
2. Missing value handling  
3. Outlier detection  
4. Feature scaling (Min-Max / Standardization)  
5. Feature selection  

---

## 3.3 Machine Learning Layer

Models implemented:

- Logistic Regression  
- Support Vector Machine (SVM)  
- Random Forest  
- Gradient Boosting  
- Artificial Neural Network (ANN)  

Model performance is evaluated using cross-validation.

---

## 3.4 Deployment Layer

- Web Interface using Streamlit / Flask  
- Risk visualization dashboard  
- Downloadable PDF/CSV report  

---

# 4️⃣ Methodology

The project follows a structured AI workflow:

1. Data Acquisition  
2. Exploratory Data Analysis (EDA)  
3. Feature Engineering  
4. Model Training  
5. Hyperparameter Tuning  
6. Model Evaluation  
7. Deployment  

---

# 5️⃣ Algorithm Description

## 5.1 Saliva Weighted Biomarker Risk Algorithm (SWBRA)

A hybrid model combining statistical thresholding and supervised machine learning.

---

### 5.1.1 Biomarker Normalization

Each biomarker is normalized:

Normalized Value = (X - Min) / (Max - Min)

This ensures uniform feature contribution.

---

### 5.1.2 Risk Weight Assignment

Each abnormal biomarker contributes to a cumulative risk score.

Example:

- Elevated IL-6 → +30  
- High CRP → +25  
- Low IgA → +20  
- Abnormal pH → +15  

---

### 5.1.3 Final Risk Classification

| Risk Score | Category |
|------------|----------|
| 0 – 39 | Low Risk |
| 40 – 69 | Moderate Risk |
| ≥ 70 | High Risk |

---

# 6️⃣ Performance Evaluation Metrics

- Accuracy  
- Precision  
- Recall  
- F1 Score  
- ROC-AUC  
- Confusion Matrix  

---

# 7️⃣ Repository Structure
