package com.example.films;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Бесконечная кирпичная стена.
 * Картина стены повторяется во все стороны (каждую вторую плитку сделала зеркальной, чтоб не было швов),
 * а метод setOffset() сдвигает стену для эффекта движения камеры
 */
public class WallView extends View {

    private static final float TILE_WIDTH_DP = 520f; // ширина одной плитки стены на экране

    private final Paint paint = new Paint();
    private final Matrix matrix = new Matrix();
    private final BitmapShader shader;
    private final float scale;
    private float offsetX = 0f;
    private float offsetY = 0f;

    public WallView(Context context) {
        this(context, null);
    }

    public WallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_wall);
        // MIRROR - плитка повторяется зеркально по горизонтали и вертикали
        shader = new BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
        paint.setShader(shader);
        scale = TILE_WIDTH_DP * getResources().getDisplayMetrics().density / bitmap.getWidth();
        updateMatrix();
    }

    /** Сдвигает стену на (x, y) пикселей. */
    public void setOffset(float x, float y) {
        offsetX = x;
        offsetY = y;
        updateMatrix();
        invalidate();
    }

    private void updateMatrix() {
        matrix.setScale(scale, scale);
        matrix.postTranslate(offsetX, offsetY);
        shader.setLocalMatrix(matrix);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
