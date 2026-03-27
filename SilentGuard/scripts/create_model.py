#!/usr/bin/env python3
"""
Silent Guard - TensorFlow Lite Model Creator
Creates a simple dummy model for testing the app without training data.
"""

import numpy as np
import os

try:
    import tensorflow as tf
    print("✓ TensorFlow found:", tf.__version__)
except ImportError:
    print("✗ TensorFlow not installed. Install with: pip install tensorflow")
    exit(1)

def create_dummy_model():
    """
    Create a simple dummy model that accepts mel spectrograms
    and outputs 3 classes (distress, neutral, other)
    """
    print("\n=== Creating Dummy Model ===")
    
    # Input shape: (batch, time_steps, mel_bins)
    # For 2-second audio at 16kHz with 64 mel bins and 10ms hop:
    # time_steps = 2 * 100 = 200
    input_shape = (200, 64)
    
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=input_shape),
        tf.keras.layers.Reshape((200, 64, 1)),
        tf.keras.layers.Conv2D(16, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Conv2D(32, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.GlobalAveragePooling2D(),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(3, activation='softmax')  # distress, neutral, other
    ], name='silent_guard_audio')
    
    model.compile(
        optimizer='adam',
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    model.summary()
    return model

def convert_to_tflite(model, output_path):
    """Convert Keras model to TensorFlow Lite format"""
    print("\n=== Converting to TFLite ===")
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]  # Use FP16 for smaller size
    
    tflite_model = converter.convert()
    
    # Save
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    file_size = os.path.getsize(output_path) / 1024  # KB
    print(f"✓ Model saved to: {output_path}")
    print(f"✓ Model size: {file_size:.2f} KB")

def test_model(tflite_path):
    """Test the TFLite model with dummy data"""
    print("\n=== Testing Model ===")
    
    # Load TFLite model
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    # Get input/output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"Input shape: {input_details[0]['shape']}")
    print(f"Output shape: {output_details[0]['shape']}")
    
    # Create dummy input
    input_shape = input_details[0]['shape']
    input_data = np.random.randn(*input_shape).astype(np.float32)
    
    # Run inference
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    
    # Get output
    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    print(f"\nTest inference output: {output_data}")
    print(f"Predicted class: {np.argmax(output_data)} (0=distress, 1=neutral, 2=other)")
    print("✓ Model working correctly!")

def download_yamnet():
    """
    Download pre-trained YAMNet model from TensorFlow Hub
    (More realistic than dummy model)
    """
    print("\n=== Downloading YAMNet ===")
    
    try:
        import tensorflow_hub as hub
        
        # Load YAMNet
        yamnet_url = 'https://tfhub.dev/google/yamnet/1'
        print(f"Loading YAMNet from {yamnet_url}...")
        
        yamnet_model = hub.load(yamnet_url)
        print("✓ YAMNet loaded successfully")
        
        # YAMNet expects (batch, samples) where samples = 16000 * seconds
        # We'll wrap it to accept mel spectrograms instead
        
        print("\nNote: YAMNet requires audio samples, not mel spectrograms.")
        print("For Silent Guard integration, you'll need to:")
        print("1. Modify AudioClassifier.kt to pass raw audio to model")
        print("2. Or train a custom model on mel spectrograms")
        
        return yamnet_model
        
    except ImportError:
        print("✗ TensorFlow Hub not installed")
        print("Install with: pip install tensorflow-hub")
        return None

def main():
    print("=" * 50)
    print("SILENT GUARD - MODEL CREATOR")
    print("=" * 50)
    
    # Ask user what they want to do
    print("\nOptions:")
    print("1. Create dummy model (fast, for testing)")
    print("2. Download YAMNet (realistic, requires TF Hub)")
    print("3. Both")
    
    choice = input("\nChoose option (1/2/3): ").strip()
    
    output_dir = "../app/src/main/assets"
    os.makedirs(output_dir, exist_ok=True)
    
    if choice in ["1", "3"]:
        # Create dummy model
        model = create_dummy_model()
        output_path = os.path.join(output_dir, "distress_audio_model.tflite")
        convert_to_tflite(model, output_path)
        test_model(output_path)
        print(f"\n✓ Dummy model ready at: {output_path}")
    
    if choice in ["2", "3"]:
        # Download YAMNet
        yamnet = download_yamnet()
        if yamnet:
            print("\n✓ YAMNet downloaded (see notes above for integration)")
    
    print("\n" + "=" * 50)
    print("DONE! You can now build the Android app.")
    print("=" * 50)
    
    print("\nNext steps:")
    print("1. Open Silent Guard in Android Studio")
    print("2. Build & Run")
    print("3. The app will use the model from assets/")
    print("\nSee QUICKSTART.md for more details.")

if __name__ == "__main__":
    main()
