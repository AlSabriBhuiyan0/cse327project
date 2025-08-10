# HappyChat AI ✨

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/google-ai-edge/gallery)](https://github.com/google-ai-edge/gallery/releases)

**Explore, Experience, and Evaluate the Future of On-Device Generative AI with HappyChat AI.**

HappyChat AI is an experimental app that puts the power of cutting-edge Generative AI models directly into your hands, running entirely on your Android *(available now)* and iOS *(coming soon)* devices. Dive into a world of creative and practical AI use cases, all running locally, without needing an internet connection once the model is loaded. Experiment with different models, chat, ask questions with images, explore prompts, and more!

**Overview**
<img width="1532" alt="Overview" src="https://github.com/user-attachments/assets/4f2702d7-91a0-4eb3-aa76-58bc8e7089c6" />

**Ask Image**
<img width="1532" alt="Ask Image" src="https://github.com/user-attachments/assets/e2b5b41b-fed0-4a7c-9547-2abb1c10962c" />

**Prompt Lab**
<img width="1532" alt="Prompt Lab" src="https://github.com/user-attachments/assets/22e459d0-0365-4a92-8570-fb59d4d1e320" />

**AI Chat**
<img width="1532" alt="AI Chat" src="https://github.com/user-attachments/assets/edaa4f89-237a-4b84-b647-b3c4631f09dc" />

## ✨ Core Features

*   **📱 Run Locally, Fully Offline:** Experience the magic of GenAI without an internet connection. All processing happens directly on your device.
*   **🤖 Choose Your Model:** Easily switch between different models from Hugging Face and compare their performance.
*   **🖼️ Ask Image:** Upload an image and ask questions about it. Get descriptions, solve problems, or identify objects.
*   **✍️ Prompt Lab:** Summarize, rewrite, generate code, or use freeform prompts to explore single-turn LLM use cases.
*   **💬 AI Chat:** Engage in multi-turn conversations.
*   **📊 Performance Insights:** Real-time benchmarks (TTFT, decode speed, latency).
*   **🧩 Bring Your Own Model:** Test your local LiteRT `.task` models.
*   **🔗 Developer Resources:** Quick links to model cards and source code.

## ⬇️ Model Download & Management

### Downloading Models

1. **Browse Available Models**
   - Navigate to the "Models" section in the app
   - Browse through the list of available models with their details (size, performance metrics, etc.)
   - Tap on a model to see more details and download options

2. **Download Process**
   - Tap the "Download" button next to your chosen model
   - For private models, you'll be prompted to enter your Hugging Face access token
   - Monitor download progress in real-time with the progress indicator
   - Pause and resume downloads at any time (coming soon)
   - Cancel ongoing downloads if needed

3. **Authentication**
   - Private models require Hugging Face authentication
   - Your access token is stored securely on-device
   - You can manage or revoke tokens at any time in the app settings

### Managing Downloads

- **View Active Downloads**: See all ongoing downloads in the notification shade
- **Cancel Downloads**: Swipe left on a download in the notification or tap "Cancel" in the model details
- **Download Queue**: Multiple downloads are queued and processed sequentially
- **Storage Management**: The app shows available storage space and warns when space is low

### Troubleshooting

- **Failed Downloads**: Failed downloads can be retried with a single tap
- **Network Issues**: The app handles network interruptions gracefully and will resume when connectivity is restored
- **Storage Issues**: Clear downloaded models or free up space if you encounter storage-related errors

## 🏁 Get Started in Minutes!

1.  **Download the App:** Grab the [**latest APK**](https://github.com/google-ai-edge/gallery/releases/latest/download/ai-edge-gallery.apk).
2.  **Install & Explore:** For detailed installation instructions (including for corporate devices) and a full user guide, head over to our [**Project Wiki**](https://github.com/google-ai-edge/gallery/wiki)!

## 🛠️ Technology Highlights

*   **Google AI Edge:** Core APIs and tools for on-device ML.
*   **LiteRT:** Lightweight runtime for optimized model execution.
*   **LLM Inference API:** Powering on-device Large Language Models.
*   **Hugging Face Integration:** For model discovery and download.

## 🤝 Feedback

This is an **experimental Alpha release**, and your input is crucial!

*   🐞 **Found a bug?** [Report it here!](https://github.com/google-ai-edge/gallery/issues/new?assignees=&labels=bug&template=bug_report.md&title=%5BBUG%5D)
*   💡 **Have an idea?** [Suggest a feature!](https://github.com/google-ai-edge/gallery/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=%5BFEATURE%5D)

## 📄 License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## 🔗 Useful Links

*   [**Project Wiki (Detailed Guides)**](https://github.com/google-ai-edge/gallery/wiki)
*   [Hugging Face LiteRT Community](https://huggingface.co/litert-community)
*   [LLM Inference guide for Android](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
*   [Google AI Edge Documentation](https://ai.google.dev/edge)
