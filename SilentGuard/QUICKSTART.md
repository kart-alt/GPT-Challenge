# 🚀 QUICK START GUIDE - Silent Guard

## 📋 Pre-Flight Checklist

Before running the app, ensure you have:
- [ ] Android Studio installed
- [ ] Physical Android device (emulators have limited sensor support)
- [ ] USB debugging enabled on device
- [ ] Android SDK 26+ installed

---

## ⚡ 5-Minute Setup

### Step 1: Import Project
```bash
1. Open Android Studio
2. File → Open → Navigate to SilentGuard folder
3. Wait for Gradle sync (2-3 minutes)
```

### Step 2: Handle the Missing ML Model

The app references a TensorFlow Lite model (`distress_audio_model.tflite`) that doesn't exist yet. You have **two options**:

#### Option A: Quick Demo (No Model) ⭐ RECOMMENDED
The app will work without the model using fallback detection:

**No action needed!** The AudioClassifier has built-in fallback logic.

#### Option B: Add a Dummy Model (For Testing)
```bash
# Create empty model file
mkdir -p app/src/main/assets
touch app/src/main/assets/distress_audio_model.tflite
```

Or download a pre-trained emotion model:
- [YAMNet from TensorFlow Hub](https://tfhub.dev/google/yamnet/1)
- Convert to TFLite format
- Place in `app/src/main/assets/`

### Step 3: Create Placeholder Icons

Create simple drawable icons:

```bash
# You need to add these drawables:
# - res/drawable/ic_launcher.png (108x108)
# - res/drawable/ic_launcher_round.png (108x108) 
# - res/drawable/ic_shield.png (24x24)
```

**Quick fix:** Use Android Studio's Asset Studio:
1. Right-click `res/drawable` → New → Image Asset
2. Icon Type: Launcher Icons
3. Name: ic_launcher
4. Use clipart (shield icon)

### Step 4: Build & Run
```bash
1. Connect Android phone via USB
2. Enable USB debugging
3. Click "Run" (green play button) or Shift+F10
4. Select your device
5. Wait for build (first time: 3-5 minutes)
```

---

## 🧪 Testing the App

### Test 1: Basic Startup
```
Expected:
✅ Onboarding screen appears
✅ Can add contact
✅ Permissions requested
✅ Main dashboard loads
```

### Test 2: Start Protection
```
1. Tap "Start Protection"
2. Grant permissions if prompted
3. Notification should appear: "Silent Guard is protecting you"

Expected:
✅ Toggle button changes to "Stop Protection"
✅ Status text: "✓ You are protected"
✅ Service runs in background
```

### Test 3: Simulate Distress (Manual)

Since you don't have the ML model, test the system manually:

**Option 1: Modify Thresholds (Easy Testing)**

Edit `DecisionEngine.kt` line 30:
```kotlin
// Change from 0.75 to 0.10 for easy triggering
private var highConfidenceThreshold = 0.10f
```

**Option 2: Create Test Activity**

Add button to MainActivity that calls:
```kotlin
// Simulate high distress scores
audioClassifier.analyzeAudio(floatArrayOf(...)) // Returns 0.9
motionAnalyzer.detectPanicMotion() // Returns 0.8
```

### Test 4: Alert Flow
```
When alert triggers:
1. ✅ Phone plays loud alarm
2. ✅ Phone vibrates
3. ✅ Red cancel screen appears
4. ✅ Countdown from 10 seconds
5. ✅ Can cancel alert
6. ✅ If not cancelled → SMS sent (check logs)
```

---

## 🐛 Common Issues & Fixes

### Issue 1: "Model file not found"
**Fix:** This is expected. The app will use fallback detection.

**Log output:**
```
W/AudioClassifier: Model file not found, using dummy model
```

### Issue 2: Build error - "Unresolved reference: R"
**Fix:** 
1. Build → Clean Project
2. Build → Rebuild Project
3. Invalidate Caches → Restart

### Issue 3: Permissions not working
**Fix:**
1. Go to Settings → Apps → Silent Guard
2. Manually grant all permissions
3. Disable battery optimization

### Issue 4: Service stops after a while
**Fix:**
1. Disable battery optimization for Silent Guard
2. Check if "Auto-start" is enabled (Xiaomi/Huawei)
3. Lock app in recent apps

### Issue 5: Audio recording fails
**Fix:**
1. Check if another app is using microphone
2. Restart device
3. Verify permission granted

---

## 📊 Monitoring Logs

Use Logcat to see detection in action:

```bash
# Filter by tag
adb logcat -s AudioClassifier MotionAnalyzer DecisionEngine

# Expected output:
D/AudioClassifier: Audio score: 0.234
D/MotionAnalyzer: Motion score: 0.156
D/DecisionEngine: Final confidence: 0.182 → NO_ALERT
```

---

## 🎯 Demo Preparation

### For Competition Demo:

1. **Pre-record video** showing:
   - App startup
   - Protection enabled
   - Alert triggered (use modified thresholds)
   - Cancel functionality
   - SMS sent confirmation

2. **Prepare backup**:
   - Video file on USB drive
   - Screenshots of key screens
   - Fallback presentation

3. **Test scenarios**:
   - Happy path (normal operation)
   - Alert trigger + cancel
   - Alert trigger + send
   - False alarm prevention (gym mode)

---

## 🔥 Quick Fixes Before Demo

### Fix 1: Reduce Detection Threshold
```kotlin
// In DecisionEngine.kt
private var highConfidenceThreshold = 0.30f // Lower from 0.75
```

### Fix 2: Add Test Button
Add to `activity_main.xml`:
```xml
<Button
    android:id="@+id/testAlertButton"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="TEST ALERT (Demo Only)"
    android:background="@android:color/holo_orange_dark" />
```

In `MainActivity.kt`:
```kotlin
testAlertButton.setOnClickListener {
    // Trigger test alert
    val alertManager = AlertManager(this)
    alertManager.triggerAlert(0.95f, emptyList())
}
```

### Fix 3: Disable SMS (Safe Testing)
Comment out in `AlertManager.kt` line 150:
```kotlin
// smsManager.sendTextMessage(...) // Disabled for testing
Log.d(TAG, "SMS would be sent to: $phoneNumber")
```

---

## 📦 What's Included

### Core Components ✅
- [x] Audio Classifier (with fallback)
- [x] Motion Analyzer
- [x] Context Validator
- [x] Decision Engine
- [x] Background Service
- [x] Alert Manager
- [x] Main UI
- [x] Onboarding flow

### What's Missing (Optional)
- [ ] Trained ML model
- [ ] Comprehensive icon set
- [ ] Settings activity (stub only)
- [ ] Detection history/logs UI
- [ ] Advanced tuning controls

---

## 🎬 Next Steps

1. **Test on real device** (30 min)
2. **Tune thresholds** (1 hour)
3. **Record demo video** (1 hour)
4. **Prepare pitch deck** (2 hours)
5. **Practice presentation** (1 hour)

---

## 🆘 Emergency Contacts

If stuck during development:
- Check `README.md` for architecture details
- Review Logcat output
- Test individual components separately
- Use fallback/dummy data

---

**You're ready to go! 🚀**

Build → Run → Test → Demo → Win! 🏆
