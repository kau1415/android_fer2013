package com.boribap.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter; // 핵심모듈

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final int FROM_ALBUM = 1; // onActivityResult 식별자
    private static final int FROM_CAMERA = 2; // 나중에 카메라 사용을 위해....

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // R.id.button_1 : 첫번째 버튼을 가리키는 id
        // setOnClickListener : 버튼이 눌렸을 때 호출될 함수 설정
        // 인텐트의 결과는 onActivityResult 함수에서 수신
        // 여러 개의 인텐트를 동시에 사용하기 때문에 숫자를 통해 결과 식별 (FORM_ALBUM)
        findViewById(R.id.button_1).setOnClickListener(new View.OnClickListener(){
            // 리스너의 기능 중에서 클릭(single touch) 사용
            @Override
            public void onClick(View v){
                Intent intent = new Intent();
                intent.setType("image/*"); // 이미지만
                intent.setAction(Intent.ACTION_GET_CONTENT); // 카메라 (ACTION_IMAGE_CAPTURE)
                startActivityForResult(intent, FROM_ALBUM);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        // 카메라를 다루지 않기 때문에 앨범 상수에 대해서 성공한 경우에 대해서만 처리
        if(requestCode != FROM_ALBUM || resultCode != RESULT_OK){
            return;
        }

        try{
            // 선택한 이미지에서 비트맵 생성
            InputStream stream = getContentResolver().openInputStream(data.getData());
            Bitmap bmp = BitmapFactory.decodeStream(stream);
            stream.close();

            ImageView iv = findViewById(R.id.face_photo);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setImageBitmap(bmp);

            // 입력 배열 생성
            float[][][][] bytes_img = new float[1][48][48][1];

            for(int y = 0; y<48; y++) {
                for (int x = 0; x < 48; x++) {
                    int pixel = bmp.getPixel(x, y);
                    bytes_img[0][x][y][0] = (float) (0.2999* Color.red(pixel) + 0.587*Color.green(pixel) + 0.114*Color.blue(pixel)) / (float) 255;
                    //bytes_img[0][x][y][0] = (pixel&0xff) / (float) 255;
                    bytes_img[0][x][y][0] = bytes_img[0][x][y][0] - (float) 0.5;
                    bytes_img[0][x][y][0] = bytes_img[0][x][y][0] * (float) 0.2;
                    Log.e("바이트 이미지", Float.toString(bytes_img[0][x][y][0]));
                }
            }

            // 파이썬에서 만든 모델 파일 로딩
            Interpreter tf_lite = getTfliteInterpreter("converted_model.tflite");
            Log.d("model", "model pull");

            // 출력 배열 생성
            // ["angry","disgust","scared", "happy", "sad", "surprised","neutral"]

            float[][] output = new float[1][7];
            tf_lite.run(bytes_img, output);

            Log.d("predict", Arrays.toString(output[0]));

            //텍스트뷰 2개. 0~9 사이의 숫자 예측
            int[] id_Array = {R.id.result_1, R.id.result_2, R.id.result_3, R.id.result_4, R.id.result_5, R.id.result_6, R.id.result_7,};

            for(int i=0; i<7; i++){
                TextView tv = findViewById(id_Array[i]);
                tv.setText(String.format("%f", output[0][i]));
                Log.d("result", String.format("%f", output[0][i]));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    // 모델 파일 인터프리터를 생성하는 공통 함수
    // loadModelFile 함수에 예외가 포함되어 있기 때문에 반드시 try, catch 블록이 필요하다
    private Interpreter getTfliteInterpreter(String modelPath){
        try {
            Log.d("interpreter", "interpreter run ");
            return new Interpreter(loadModelFile(MainActivity.this, modelPath));
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    // 모델을 읽어오는 함수
    // MappedByteBuffer 바이트 버퍼를 Interpreter 객체에 전달하면 모델 해석을 할 수 있다.
    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        long startOffset = fileDescriptor.getStartOffset();
        long declaredLegth = fileDescriptor.getDeclaredLength();
        Log.d("interpreter", "model_path  :  " + modelPath);
        Log.d("interpreter", "loadModel run");

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLegth);
    }
}
