<div align="center">
    <h1>OnlySq App</h1>
    <img width="1233" height="741" alt="image" src="https://github.com/user-attachments/assets/9ceda638-abf6-4c48-90a8-d135cadb426f" />
    AI chatbot features using OnlySq
    <p>Made by <bold>~$ sudo++</bold></p>
    <img alt="code size" src="https://img.shields.io/github/languages/code-size/imsudoer/OnlySqApp?style=for-the-badge">
    <img alt="repo stars" src="https://img.shields.io/github/stars/imsudoer/OnlySqApp?style=for-the-badge">
    <img alt="repo stars" src="https://img.shields.io/github/commit-activity/w/imsudoer/OnlySqApp?style=for-the-badge">
</div>

---

# OnlySq AI Chat Client

A professional desktop application designed for interacting with artificial intelligence models via the OnlySq API. Built using Kotlin and Compose Multiplatform, this client provides a robust environment for managing AI-driven conversations with a focus on performance and clean user interface design.

---

## Technical Overview

The application leverages **Compose Multiplatform** to provide a native desktop experience. It utilizes an asynchronous architecture powered by **Kotlin Coroutines** and **Ktor** to handle high-frequency data streaming and API communication without compromising UI responsiveness.

### Key Functionalities

* **Dynamic Model Selection**: Integration with the OnlySq model registry, allowing users to search and switch between various Large Language Models (LLMs) such as GPT and Qwen variants.
* **Asynchronous Streaming**: Support for Server-Sent Events (SSE) to render AI responses in real-time, reducing perceived latency.
* **Automated Context Management**: Features an automated titration system that generates concise chat titles based on the initial conversation context.
* **Persistent Storage**: Local encryption and storage of chat history and API configurations to ensure data continuity across sessions.
* **Markdown Integration**: Advanced rendering of structured text, including code blocks and mathematical notation within the chat interface.

---

## Architecture and Stack

* **Framework**: Compose Multiplatform (Material 3)
* **Networking**: Ktor Client (ContentNegotiation, Logging, and Streaming)
* **Data Handling**: Kotlinx Serialization (JSON)
* **Concurrency**: Kotlin Coroutines and Flow
* **Styling**: Adaptive Light/Dark color schemes based on system telemetry

---

## Installation and Deployment

### System Requirements

* Java Development Kit (JDK) 17 or higher
* Valid OnlySq API Credentials

### Build Instructions

To compile and execute the application from source, utilize the Gradle wrapper:

```bash
./gradlew run

```

To package the application for distribution (MSI, DMG, or DEB):

```bash
./gradlew packageDistributionForCurrentOS

```

---

## Configuration

Upon initial launch, navigation to the **Options** section is required to input the API key.

1. Access **Options** via the sidebar.
2. Provide the **OnlySq API Key**.
3. Configure the **Streaming** toggle based on preference for real-time data transmission versus batch processing.

---

## User Interface Features

* **Responsive Layout**: The interface dynamically adjusts between a multi-pane desktop view and a collapsed mobile-style menu for narrower window dimensions.
* **Input Handling**: Support for multi-line text entry with standard keyboard shortcuts (`Shift + Enter` for line breaks, `Enter` for submission).
* **Clipboard Integration**: One-click copying of AI-generated content to the system clipboard.
