// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.examples.hands;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.exifinterface.media.ExifInterface;
// ContentResolver dependency
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;

import org.tensorflow.lite.Interpreter;


/** Main activity of MediaPipe Hands app. */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  private Hands hands;
  // Run the pipeline and the model inference on GPU or CPU.
  private static final boolean RUN_ON_GPU = true;
  public static String MODEL_FILENAME = "model.tflite";

  // Интерпретатор модели нейронной сети
  protected Interpreter tflite;

  // Счетчик фреймов
  private int FramesCount;

  // Счетчик пустых фреймов
  private int emptyFrames = 0;

  // Входной массив координат
  private float[][][] inp;
  private float[][] out;

  // Словарь
  private List<String> labels;

  // Переведенное предложение/слово
  private String label;
  private enum InputSource {
    UNKNOWN,
    CAMERA,
  }
  private InputSource inputSource = InputSource.UNKNOWN;

  // Image demo UI and image loader components.
  private ActivityResultLauncher<Intent> imageGetter;
  private HandsResultImageView imageView;
  // Video demo UI and video loader components.
  private VideoInput videoInput;
  private ActivityResultLauncher<Intent> videoGetter;
  // Live camera demo UI and camera components.
  private CameraInput cameraInput;
  private TextView textView;
  private FrameLayout frameLayout;
  boolean isFrontCamera;
  boolean isPressed;

  private SolutionGlSurfaceView<HandsResult> glSurfaceView;

  private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
    AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILENAME);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }
  private final Handler handler = new Handler();

  private void change() {
    handler.post(run);
  }
  private Runnable run = new Runnable() {
    @Override
    public void run() {
      textView.setText(label);
      handler.postDelayed(this, 1000);
    }
  };


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    setupStaticImageDemoUiComponents();
    setupVideoDemoUiComponents();
    setupLiveDemoUiComponents();

    inp = new float[1][20][126];
    out = new float[1][16];
    FramesCount = 0;
    labels = Arrays.asList("Я", "в школе", "ты/(у) тебя/с тобой", "Привет", "дела", "До свидания", "зовут",
            "Как", "люблю", "нет", "Здравствуйте", "Можно", "Вы/(у) Вас/с Вами",
            "Познакомиться", "где", "учишься / учитесь");
    label = "Перевод";
    isFrontCamera = true;
    isPressed = false;
    textView = (TextView) findViewById(R.id.no_view);
    frameLayout = findViewById(R.id.preview_display_layout);
    // Каждые 1000 мс обновляется на экране записываемое в переменную переведенное предложение
    change();

  }
  public void StartNewActivity(View v) {
    Intent intent = new Intent(this, Secons_activity.class);
    startActivity(intent);
  }
  @Override
  protected void onResume() {
    super.onResume();
    if (inputSource == InputSource.CAMERA) {
      // Restarts the camera and the opengl surface rendering.
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
      glSurfaceView.post(this::startCamera);
      glSurfaceView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (inputSource == InputSource.CAMERA) {
      glSurfaceView.setVisibility(View.GONE);
      cameraInput.close();
    }
  }

  private Bitmap downscaleBitmap(Bitmap originalBitmap) {
    double aspectRatio = (double) originalBitmap.getWidth() / originalBitmap.getHeight();
    int width = imageView.getWidth();
    int height = imageView.getHeight();
    if (((double) imageView.getWidth() / imageView.getHeight()) > aspectRatio) {
      width = (int) (height * aspectRatio);
    } else {
      height = (int) (width / aspectRatio);
    }
    return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
  }

  private Bitmap rotateBitmap(Bitmap inputBitmap, InputStream imageData) throws IOException {
    int orientation =
        new ExifInterface(imageData)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    if (orientation == ExifInterface.ORIENTATION_NORMAL) {
      return inputBitmap;
    }
    Matrix matrix = new Matrix();
    switch (orientation) {
      case ExifInterface.ORIENTATION_ROTATE_90:
        matrix.postRotate(90);
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        matrix.postRotate(180);
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        matrix.postRotate(270);
        break;
      default:
        matrix.postRotate(0);
    }
    return Bitmap.createBitmap(
        inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);
  }

  /** Sets up the UI components for the static image demo. */
  private void setupStaticImageDemoUiComponents() {
    // The Intent to access gallery and read images as bitmap.
    imageGetter =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              Intent resultIntent = result.getData();
              if (resultIntent != null) {
                if (result.getResultCode() == RESULT_OK) {
                  Bitmap bitmap = null;
                  try {
                    bitmap =
                        downscaleBitmap(
                            MediaStore.Images.Media.getBitmap(
                                this.getContentResolver(), resultIntent.getData()));
                  } catch (IOException e) {
                    Log.e(TAG, "Bitmap reading error:" + e);
                  }
                  try {
                    InputStream imageData =
                        this.getContentResolver().openInputStream(resultIntent.getData());
                    bitmap = rotateBitmap(bitmap, imageData);
                  } catch (IOException e) {
                    Log.e(TAG, "Bitmap rotation error:" + e);
                  }
                  if (bitmap != null) {
                    hands.send(bitmap);
                  }
                }
              }
            });

    imageView = new HandsResultImageView(this);
  }

  /** Sets up the UI components for the video demo. */
  private void setupVideoDemoUiComponents() {
    // The Intent to access gallery and read a video file.
    videoGetter =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              Intent resultIntent = result.getData();
              if (resultIntent != null) {
                if (result.getResultCode() == RESULT_OK) {
                  glSurfaceView.post(
                      () ->
                          videoInput.start(
                              this,
                              resultIntent.getData(),
                              hands.getGlContext(),
                              glSurfaceView.getWidth(),
                              glSurfaceView.getHeight()));
                }
              }
            });
  }

  /** Sets up the UI components for the live demo with camera input. */
  private void setupLiveDemoUiComponents() {
    Button startCameraButtonFront = findViewById(R.id.button_start_camera_front);
    startCameraButtonFront.setOnClickListener(
        v -> {
          isFrontCamera = true;
          if (!isPressed) {
            stopCurrentPipeline();
          }
          isPressed = false;
          setupStreamingModePipeline(InputSource.CAMERA);
        });
    Button startCameraButtonBack = findViewById(R.id.button_start_camera_back);
    startCameraButtonBack.setOnClickListener(
        v -> {
          isFrontCamera = false;
          if (!isPressed) {
            stopCurrentPipeline();
          }
          isPressed = false;
          setupStreamingModePipeline(InputSource.CAMERA);
        });
    Button textTranslate = findViewById(R.id.button_text_translate);
    textTranslate.setOnClickListener(
            view -> {
              if (!isPressed) {
                stopCurrentPipeline();
              }
              StartNewActivity(view);
              isPressed = true;
            }
    );
  }

  /** Sets up core workflow for streaming mode. */
  private void setupStreamingModePipeline(InputSource inputSource) {
    this.inputSource = inputSource;
    // Initializes a new MediaPipe Hands solution instance in the streaming mode.
    hands =
        new Hands(
            this,
            HandsOptions.builder()
                .setStaticImageMode(false)
                .setMaxNumHands(2)
                .setRunOnGpu(RUN_ON_GPU)
                .build());
    hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

    if (inputSource == InputSource.CAMERA) {
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
    }

    // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
    glSurfaceView =
        new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
    glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
    glSurfaceView.setRenderInputImage(true);
    hands.setResultListener(
        handsResult -> {
          logWristLandmark(handsResult, /*showPixelValues=*/ false);
          glSurfaceView.setRenderData(handsResult);
          glSurfaceView.requestRender();
        });

    // The runnable to start camera after the gl surface view is attached.
    // For video input source, videoInput.start() will be called when the video uri is available.
    if (inputSource == InputSource.CAMERA) {
      glSurfaceView.post(this::startCamera);
    }

    // Updates the preview layout.
    imageView.setVisibility(View.GONE);
    frameLayout.removeAllViewsInLayout();
    frameLayout.addView(glSurfaceView);
    glSurfaceView.setVisibility(View.VISIBLE);
    frameLayout.requestLayout();
  }

  private void startCamera() {
    CameraInput.CameraFacing facing;

    if (isFrontCamera) {
      facing = CameraInput.CameraFacing.FRONT;
    } else {
      facing = CameraInput.CameraFacing.BACK;
    }
    // При смене положения камеры, обнуляются такие переменные, как
    // переведенное предложение, счетчики путсых и непустых фреймов,
    // входной массив координат
    label = "Перевод";
    emptyFrames = 0;
    inp = new float[1][20][126];
    out = new float[1][16];
    FramesCount = 0;

    cameraInput.start(
        this,
        hands.getGlContext(),
        facing,
        glSurfaceView.getWidth(),
        glSurfaceView.getHeight());
  }

  private void stopCurrentPipeline() {
    if (cameraInput != null) {
      cameraInput.setNewFrameListener(null);
      cameraInput.close();
    }
    if (glSurfaceView != null) {
      glSurfaceView.setVisibility(View.GONE);
    }
    if (hands != null) {
      hands.close();
    }
  }

  private void logWristLandmark(HandsResult result, boolean showPixelValues) {
    if (result.multiHandWorldLandmarks().isEmpty()) {
      emptyFrames++;
      // Когда счетчик фреймов, в которых не обнаружилось рук, достигает 5,
      // он обнуляется, как и строка переведнного предложения, и счетчик фреймов,
      // и массив координат
      if (emptyFrames == 5) {
        emptyFrames = 0;
        inp = new float[1][20][126];
        out = new float[1][16];
        FramesCount = 0;
        label = "Перевод";
      }
      return;
    }

    int numHands = result.multiHandWorldLandmarks().size();

    if (!showPixelValues) {
      collectLandmarks(numHands, result);
      ++FramesCount;
      // Когда счетчик феймов достигает 20, на вход нейронной сети подается массив координат,
      // модель подключается, делает предсказание и выключается, а полученное слово записывается в предложение.
      // Массив координат заново начинает заполняться, счетчик фреймов обнуляется
      if (FramesCount == 20) {
        try {
          tflite = new Interpreter(loadModelFile(this));
          tflite.run(inp, out);
          int index = getMax(out[0]);
          tflite.close();
          String word = labels.get(index);
          label = word;
          //makeSentence(word);

        } catch (IOException e) {
          e.printStackTrace();
        }

        FramesCount = 0;
        inp = new float[1][20][126];
        out = new float[1][16];
      }
    }
  }

  private int getMax(float[] arr){
    int max = 0;
    for (int i=0; i<arr.length; i++) {
      if (arr[i] > arr[max]) max=i;
    }
    return max;
  }

  private void makeSentence(String word) {
    if (label.startsWith("Можно")) {
      switch (word) {
        case "вы":
          label += " с Вами";
          break;
        case "ты":
          label += " с тобой";
          break;
        case "познакомиться":
          label += " познакомиться";
          break;
      }
    } else if (label.startsWith("Как")) {
      switch (word) {
        case "вы":
          label += " у Вас";
          break;
        case "ты":
          label += " у тебя";
          break;
        case "дела":
          label += " дела";
          break;
        case "зовут":
          if (label.equals("Как у тебя")) {
            label = "Как тебя зовут";
          } else if (label.equals("Как у Вас")) {
            label = "Как Вас зовут";
          } else {
            label = "Как зовут";
          }
          break;
      }
    } else if (label.startsWith("Я")) {
      switch (word) {
        case "ты":
          label += " тебя";
          break;
        case "вы":
          label += " Вас";
          break;
        case "любить":
          label += " люблю";
          break;
      }
    } else if (label.startsWith("Где")) {
      switch (word) {
        case "вы":
          label += " Вы";
          break;
        case "ты":
          label += " ты";
          break;
        case "учиться":
          if (label.equals("Где ты")) {
            label += " учишься";
          } else if (label.equals("Где Вы")) {
            label += " учитесь";
          } else {
            label += " учишься";
          }
          break;
      }
    } else {
      label = word.substring(0, 1).toUpperCase() + word.substring(1);
    }
  }

  // Функция, вызываемая каждый фрейм, на котором обнаружилась хотя бы одна рука.
  // В глобальный массив координат в соответствии с порядковым номером фрейма записываются
  // координаты точек (на каждой руке по 21 точке по x,y,z координате). Сначала записываются
  // сведения о левой руке, затем о правой. Если какой-то из рук не обнаружилось, то отведенное
  // для место место заполняется нулями. Поэтому начальное положение для всех точек левой руки: 0,
  // а для правой: 63
  private void collectLandmarks (int numHands, HandsResult result) {
    for (int i = 0; i < numHands; ++i) {
      // При использовании задней камеры, правая рука принимается за левую, а левая за правую
      boolean isLeftHand;
      if (isFrontCamera) {
        isLeftHand = result.multiHandedness().get(i).getLabel().equals("Left");
      } else {
        isLeftHand = result.multiHandedness().get(i).getLabel().equals("Right");
      }
      List<Landmark> landmarks = result.multiHandWorldLandmarks().get(i).getLandmarkList();

      int twoHandsLandmarksCount = 126;
      int oneHandLandmarksCount = 63;

      if (isLeftHand) {
        int coordinatesCount = 0;
        for (Landmark landmark : landmarks) {
          inp[0][FramesCount][coordinatesCount] = landmark.getX();
          inp[0][FramesCount][coordinatesCount + 1] = landmark.getY();
          inp[0][FramesCount][coordinatesCount + 2] = landmark.getZ();
          coordinatesCount += 3;
        }
        if (numHands == 1) {
          for (int k = oneHandLandmarksCount; k < twoHandsLandmarksCount; ++k) {
            inp[0][FramesCount][k] = 0.0F;
          }
        }
      } else {
        int coordinatesCount = oneHandLandmarksCount;
        for (Landmark landmark : landmarks) {
          inp[0][FramesCount][coordinatesCount] = landmark.getX();
          inp[0][FramesCount][coordinatesCount + 1] = landmark.getY();
          inp[0][FramesCount][coordinatesCount + 2] = landmark.getZ();
          coordinatesCount += 3;
        }
        if (numHands == 1) {
          for (int k = 0; k < oneHandLandmarksCount; ++k) {
            inp[0][FramesCount][k] = 0.0F;
          }
        }
      }
    }
  }
}
