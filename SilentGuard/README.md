# 🛡️ Silent Guard

**Offline, Privacy-First Emergency Detection System**

Silent Guard is an Android application that automatically detects distress situations using on-device AI, without requiring manual interaction or cloud connectivity.

---

## 🎯 Problem Statement

In real emergencies:
- Victims cannot press SOS buttons
- Phones may be in pockets or bags
- Internet may be unavailable
- Traditional safety apps fail when needed most

**Silent Guard solves this** by detecting *unintentional* distress signals—the sounds and movements you can't control when help matters most.

---

## ✨ Key Features

### 🧠 Multi-Modal Detection
- **Audio Analysis**: Detects distress vocalizations (screams, panic)
- **Motion Patterns**: Identifies struggle, falls, erratic movement
- **Context Awareness**: Filters false alarms using environmental data

### 🔒 Privacy-First Design
- ✅ All processing on-device
- ✅ No cloud uploads
- ✅ No continuous recording
- ✅ Open-source and auditable

### ⚡ Offline-First
- Works without internet
- No dependency on cloud AI
- Instant response (<1 second)

### 🎯 False Alarm Prevention
- Multi-layer validation
- Context-aware suppression
- User-configurable modes (concert, gym, etc.)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────┐
│         USER INTERFACE              │
│  - Dashboard                        │
│  - Alert cancel screen              │
│  - Settings                         │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│       DETECTION ENGINE              │
│  ┌──────────┐  ┌──────────┐        │
│  │  Audio   │  │  Motion  │        │
│  │Classifier│  │ Analyzer │        │
│  └──────────┘  └──────────┘        │
│         ↓            ↓              │
│  ┌─────────────────────────┐       │
│  │  Decision Engine        │       │
│  │  (Multi-Signal Fusion)  │       │
│  └─────────────────────────┘       │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│       RESPONSE LAYER                │
│  - Local alert (sound + vibration)  │
│  - 10-second cancel window          │
│  - SMS to trusted contacts          │
│  - Location sharing                 │
└─────────────────────────────────────┘
```

---

## 🔬 Technical Details

### Audio Classification
- **Input**: 2-second audio windows (16kHz)
- **Features**: Mel spectrograms (64 bins)
- **Model**: TensorFlow Lite (YAMNet-inspired)
- **Output**: Distress probability (0-1)

### Motion Analysis
- **Sensors**: Accelerometer + Gyroscope (50Hz)
- **Features**: Jerk, variance, frequency, fall detection
- **Method**: Rule-based (no ML training needed)

### Decision Fusion
- **Weights**: Audio (50%), Motion (30%), Context (20%)
- **Thresholds**:
  - High confidence: >0.75 → Immediate alert
  - Medium confidence: >0.50 → Require 2 consecutive
  - Low: <0.50 → Ignore

### Context Validation
- **Time of day**: Night = higher risk
- **Phone position**: Pocket = more suspicious
- **Ambient noise**: >85dB + rhythmic motion = concert (suppress)
- **User modes**: Gym/concert modes available

---

## 📊 Performance Metrics

| Metric | Target | Status |
|--------|--------|--------|
| False alarm rate | <5% | ⏳ Testing |
| Detection latency | <1s | ✅ Achieved |
| Battery drain | ~5%/day | ⏳ Optimizing |
| Offline functionality | 100% | ✅ Yes |
| Privacy compliance | Zero raw data storage | ✅ Yes |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 26+ (Android 8.0+)
- Kotlin 1.9+

### Build Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/silent-guard.git
   cd silent-guard
   ```

2. **Open in Android Studio**
   - File → Open → Select `SilentGuard` folder

3. **Sync Gradle**
   - Android Studio will auto-sync dependencies

4. **Add TensorFlow Lite Model** (Optional for full functionality)
   - Place `distress_audio_model.tflite` in `app/src/main/assets/`
   - See [Model Training Guide](docs/MODEL_TRAINING.md) for details

5. **Build & Run**
   - Click "Run" or press Shift+F10
   - Select physical device (recommended for sensor testing)

### Testing Without a Model
The app includes fallback logic and will run without the TFLite model using:
- Audio feature-based detection (energy, zero-crossing rate)
- Motion pattern analysis
- Context validation

---

## 📱 Usage

### First Launch
1. Grant permissions (microphone, location, SMS)
2. Add trusted contacts (at least 1)
3. Tap "Start Protection"

### Normal Operation
- App runs in background
- Notification shows "Silent Guard is protecting you"
- Battery-optimized (minimal drain)

### When Distress Detected
1. **Local alert**: Loud sound + vibration
2. **Cancel window**: 10 seconds to cancel
3. **If not cancelled**: SMS sent to contacts with location

### Modes
- **Normal**: Full protection
- **Gym Mode**: Reduce motion sensitivity
- **Concert Mode**: Suppress alerts in loud environments
- **Sleep Mode**: Lower sensitivity

---

## 🧪 Testing & Validation

### Simulation Tests
```kotlin
// Test distress detection
val audioScore = 0.8f  // High distress
val motionScore = 0.7f // Panic motion
val result = decisionEngine.evaluateDistress(audioScore, motionScore, context)
// Expected: HIGH_CONFIDENCE → Alert triggered
```

### False Alarm Tests
- ✅ Concert/festival environment
- ✅ Gym workout
- ✅ Sports cheering
- ✅ Watching movies
- ✅ Phone drop

### Real-World Scenarios
- Team member acts distressed (scream + panic motion)
- Verify alert triggers correctly
- Test cancel functionality

---

## 🗺️ Roadmap

### MVP (Week 1) ✅
- [x] Audio + motion detection
- [x] Background service
- [x] Alert system with cancel window
- [x] SMS notification
- [x] Basic UI

### Future Enhancements
- [ ] Wearable integration (smartwatch)
- [ ] Advanced ML model training
- [ ] Detection history/logs
- [ ] Family sharing features
- [ ] Integration with emergency services
- [ ] Voice command ("Hey Guard, I'm safe")

---

## 🔐 Privacy & Security

### Data Collection
- **Audio**: Processed in 2-second frames, never stored
- **Motion**: Sensor data processed in real-time
- **Location**: Only retrieved when alert triggered
- **Contacts**: Stored locally (SharedPreferences)

### Permissions Used
- `RECORD_AUDIO`: Distress sound detection
- `ACCESS_FINE_LOCATION`: Share location in emergencies
- `SEND_SMS`: Alert trusted contacts
- `FOREGROUND_SERVICE`: Background detection
- `WAKE_LOCK`: Keep detection running

### Security Measures
- No network communication (except SMS)
- No analytics or tracking
- No user account required
- Open-source code (auditable)

---

## 📄 License

MIT License - See [LICENSE](LICENSE) file

---

## 👥 Contributors

- **Your Name** - Lead Developer

---

## 🆘 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/silent-guard/issues)
- **Email**: support@silentguard.app
- **Documentation**: [Wiki](https://github.com/yourusername/silent-guard/wiki)

---

## 🏆 Competition Submission

This project was developed for [Competition Name] hackathon.

**Key Differentiators:**
1. Fully offline (no internet required)
2. Privacy-first (no cloud, no recording)
3. Multi-modal detection (audio + motion + context)
4. False alarm prevention (context-aware)
5. Open-source and auditable

---

## 📚 Citations

### Datasets Used
- RAVDESS: Emotional speech and song database
- UrbanSound8K: Urban sound classification
- Common Voice: Mozilla's open speech dataset

### Research Papers
- YAMNet: Google's audio event classification model
- Human Activity Recognition using smartphone sensors
- Audio-based distress detection in healthcare

---

## ⚠️ Disclaimer

Silent Guard is an experimental safety tool and should not be relied upon as the sole emergency response system. Always call local emergency services (911, 112, etc.) when in danger.

---

**Built with ❤️ for a safer world**
