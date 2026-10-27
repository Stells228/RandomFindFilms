package com.example.films;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Скорость и плавность
    private static final long WALK_DURATION_MS = 5500;   // подъём к картине (больше = медленнее)
    private static final long CRASH_DURATION_MS = 3200;  // подъём перед ударом о столб
    private static final float STEP_BOB_DP = 4f;         // приведение приподнимается на каждой ступеньке
    private static final float STEP_TILT_DEG = 2f;       // покачивание в стороны при ходьбе

    // Парение (вверх-вниз)
    private static final float HOVER_DP = 12f;           // амплитуда парения
    private static final long HOVER_PERIOD_MS = 3200;    // время одного полного цикла вверх-вниз

    // Картины
    private static final float PAINTING_SCALE = 1.0f;       // общий масштаб картины с табличкой
    private static final float PAINTING_OFFSET_X_DP = -15f;   // сдвиг картины при остановке
    private static final float PAINTING_OFFSET_Y_DP = 0f;   // сдвиг картины при остановке

    // Положение приведения
    private static final float GHOST_X_FRACTION = 0.58f;        // где стоит
    private static final float GHOST_BOTTOM_MARGIN_DP = 82f;    // отступ ножек
    private static final float GHOST_SINK_DP = 8f;
    private static final float GHOST_VISIBLE_HEIGHT_DP = 128f;

    // Паук
    private static final float SPIDER_HIDDEN_DP = 3f;

    // Параметры лестницы (stairs_strip.png)
    private static final float SLOPE = 63f / 96f;      // наклон лестницы
    private static final float STRIP_PERIOD = 96f;     // ширина одной ступеньки
    private static final float STRIP_PLANK_X = 472f;   // x середины одной из ступенек
    private static final float STRIP_PLANK_Y = 512f;   // y верхней поверхности этой ступеньки
    private static final float STAIRS_SCALE = 0.34f;

    // Доли внутри картинок
    private static final float GHOST_FEET = 0.89f;   // положение ножек в картинке приведения
    private static final float GHOST_HALF_WIDTH = 0.42f;  // половина видимой ширины приведения
    private static final float GROUP_VISIBLE_BOTTOM_DP = 507f;  // низ таблички от верха картины
    private static final float PILLAR_RIGHT_EDGE = 0.668f;
    private static final float PILLAR_CENTER = 0.508f;
    private static final float PILLAR_BOTTOM = 0.9756f;
    private static final float PILLAR_BASE_BELOW_LINE_DP = 100f;

    // Элементы экрана
    private Button btnRandom;
    private WallView wall;
    private FrameLayout content, world;
    private ImageView ghost, stairs, pillar, signNoFilms;
    private ImageView webTopLeft, webBottomRight;
    private View spiderGroup;

    // Данные
    private List<Movie> movies = new ArrayList<>();
    private final List<View> paintings = new ArrayList<>(); // готовые картины с табличками

    // Состояние
    private int stepsDone = 0;  // сколько подъёмов уже сделано (фильмы + последний удар о столб)
    private boolean isBusy = false;  // true, пока идёт анимация
    private float density;
    private float currentShift = 0f;  // на сколько сдвинут мир

    // Рассчитанные позиции
    private float period;  // ширина ступеньки на экране
    private float stepDistance;  // на сколько сдвигается мир за одно нажатие
    private float ghostBaseY;
    private float stairsBaseX, stairsBaseY;
    private float[] paintBaseX, paintBaseY;
    private float pillarBaseX, pillarBaseY;
    private final Rect clipRect = new Rect();

    // Положение приведения складывается из трёх частей
    private float walkBob = 0f; // подпрыгивание на ступеньках (px)
    private float walkTilt = 0f; // покачивание при ходьбе (градусы)
    private float hoverOffset = 0f; // парение (px)

    // Бесконечные анимации
    private ValueAnimator hoverAnimator;
    private boolean decorationsStarted = false;

    // Анимация головокружения
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ObjectAnimator swayAnimator;
    private boolean dizzyFirstFrame = true;

    private final Runnable dizzyFrames = new Runnable() {
        @Override
        public void run() {
            dizzyFirstFrame = !dizzyFirstFrame;
            ghost.setImageResource(dizzyFirstFrame
                    ? R.drawable.ghost_dizzy1 : R.drawable.ghost_dizzy2);
            handler.postDelayed(this, 350);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;

        findViews();
        applySystemBarsPadding();

        // 1. Читаем фильмы из assets/movies.json
        movies = loadMovies();
        // 2. Перемешиваем
        Collections.shuffle(movies);

        createPaintings();

        if (movies.isEmpty()) {
            btnRandom.setEnabled(false);
        }
        btnRandom.setOnClickListener(v -> onRandomClick());

        content.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        placeScene();
                    }
                });
    }

    private void findViews() {
        btnRandom = findViewById(R.id.btnRandom);
        wall = findViewById(R.id.wall);
        content = findViewById(R.id.content);
        world = findViewById(R.id.world);
        ghost = findViewById(R.id.ghost);
        stairs = findViewById(R.id.stairs);
        pillar = findViewById(R.id.pillar);
        signNoFilms = findViewById(R.id.signNoFilms);
        webTopLeft = findViewById(R.id.webTopLeft);
        webBottomRight = findViewById(R.id.webBottomRight);
        spiderGroup = findViewById(R.id.spiderGroup);
    }

    /** Отступы под строку состояния и панель навигации (стена на весь экран). */
    private void applySystemBarsPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            // после смены отступов пересчитываем расстановку
            v.post(() -> {
                if (!isBusy) placeScene();
            });
            return insets;
        });
    }

    //  Чтение JSON
    private List<Movie> loadMovies() {
        try (InputStream stream = getAssets().open("movies.json");
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

            Gson gson = new Gson();
            Movies data = gson.fromJson(reader, Movies.class);
            if (data != null && data.getMovies() != null) {
                return new ArrayList<>(data.getMovies());
            }
        } catch (IOException | JsonParseException e) {
            Log.e("MainActivity", "Ошибка чтения movies.json", e);
            Toast.makeText(this, R.string.error_load, Toast.LENGTH_LONG).show();
        }
        return new ArrayList<>();
    }

    //  Создание картин
    /** Для каждого фильма создаём картину (рамка + постер + табличка). Они стоят на стене и ждут. */
    private void createPaintings() {
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);

            // Чередуем рамки: оранжевая, золотая, оранжевая...
            int layoutId = (i % 2 == 0)
                    ? R.layout.item_painting_orange : R.layout.item_painting_gold;
            View painting = inflater.inflate(layoutId, world, false);

            ImageView poster = painting.findViewById(R.id.posterImage);
            TextView tvTitle = painting.findViewById(R.id.tvTitle);
            TextView tvInfo = painting.findViewById(R.id.tvInfo);

            poster.setImageResource(getPosterId(movie.getPoster()));
            tvTitle.setText(movie.getTitle());
            tvInfo.setText(getString(R.string.info_format,
                    movie.getYear(),
                    movie.getDirector(),
                    movie.getGenre(),
                    movie.getDuration(),
                    movie.getCountry()));

            world.addView(painting);
            paintings.add(painting);
        }
    }

    /** Находим ресурс постера по имени из JSON. */
    @SuppressLint("DiscouragedApi")
    private int getPosterId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    //  Расстановка всего на экране
    private void placeScene() {
        if (content.getWidth() == 0) return;

        // Размер области без системных панелей
        float contentW = content.getWidth() - content.getPaddingLeft() - content.getPaddingRight();
        float contentH = content.getHeight() - content.getPaddingTop() - content.getPaddingBottom();

        // 1. Где стоят ножки приведения (точка gx, gy)
        float gx = contentW * GHOST_X_FRACTION;
        float gy = contentH - GHOST_BOTTOM_MARGIN_DP * density;

        float ghostW = ghost.getWidth();
        float ghostH = ghost.getHeight();
        ghost.setTranslationX(gx - ghostW / 2f);
        ghostBaseY = gy + GHOST_SINK_DP * density - GHOST_FEET * ghostH;

        // 2. Лестница: линию ступенек проводим через ножки приведения.
        // Лестница повторяется каждые 'period' пикселей, поэтому сдвигаем её по кругу.
        float scalePx = STAIRS_SCALE * density;
        period = STRIP_PERIOD * scalePx;
        // Начальный x лестницы: ступеньки попадают под приведение, а левый край картинки
        // всегда остаётся за экраном (между -2 и -1 ширины ступеньки).
        float phaseX = (gx - scalePx * STRIP_PLANK_X) % period;
        if (phaseX < 0) phaseX += period;
        stairsBaseX = phaseX - 2 * period;
        stairsBaseY = gy - scalePx * STRIP_PLANK_Y
                - SLOPE * (gx - stairsBaseX - scalePx * STRIP_PLANK_X);

        // 3. Шаг мира за одно нажатие: целое число ступенек, около 1.1 ширины экрана
        stepDistance = Math.round(contentW * 1.1f / period) * period;

        // 4. Картины. Картина номер i должна оказаться над приведением
        if (!paintings.isEmpty()) {
            float groupW = paintings.get(0).getWidth();
            float groupTop = Math.max(8 * density,
                    (gy - GHOST_VISIBLE_HEIGHT_DP * density
                            - GROUP_VISIBLE_BOTTOM_DP * PAINTING_SCALE * density) / 2f);
            groupTop += PAINTING_OFFSET_Y_DP * density;
            float startX = gx - groupW / 2f + PAINTING_OFFSET_X_DP * density;

            paintBaseX = new float[paintings.size()];
            paintBaseY = new float[paintings.size()];
            for (int i = 0; i < paintings.size(); i++) {
                paintBaseX[i] = startX - (i + 1) * stepDistance;
                paintBaseY[i] = groupTop - (i + 1) * stepDistance * SLOPE;

                // Масштаб относительно верхней середины: картина остаётся на месте и по центру
                View painting = paintings.get(i);
                painting.setPivotX(painting.getWidth() / 2f);
                painting.setPivotY(0f);
                painting.setScaleX(PAINTING_SCALE);
                painting.setScaleY(PAINTING_SCALE);
            }
        }

        // 5. Столб: он стоит на 1 шаг дальше последней картины и появляется только в конце.
        // Он позади лестницы, а лестница обрезается по его правому краю.
        float pw = pillar.getWidth();
        float ph = pillar.getHeight();
        float pillarEndX = gx - GHOST_HALF_WIDTH * ghostW - 2 * density - PILLAR_RIGHT_EDGE * pw;
        float pillarCenterX = pillarEndX + PILLAR_CENTER * pw;
        float lineY = gy - SLOPE * (gx - pillarCenterX);          // высота ступенек у столба
        float pillarEndY = lineY + PILLAR_BASE_BELOW_LINE_DP * density - PILLAR_BOTTOM * ph;
        int total = movies.size() + 1;
        pillarBaseX = pillarEndX - total * stepDistance;
        pillarBaseY = pillarEndY - total * stepDistance * SLOPE;

        // 6. Паутина в углах и паук сверху.
        placeDecorations();

        currentShift = stepsDone * stepDistance;
        applyShift(currentShift);

        if (!decorationsStarted) {
            decorationsStarted = true;
            startHover();
        }
    }

    private void placeDecorations() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) webTopLeft.getLayoutParams();
        lp.topMargin = -content.getPaddingTop();
        lp.leftMargin = -content.getPaddingLeft();
        webTopLeft.setLayoutParams(lp);

        lp = (FrameLayout.LayoutParams) webBottomRight.getLayoutParams();
        lp.bottomMargin = -content.getPaddingBottom();
        lp.rightMargin = -content.getPaddingRight();
        webBottomRight.setLayoutParams(lp);

        lp = (FrameLayout.LayoutParams) spiderGroup.getLayoutParams();
        lp.topMargin = (int) (-SPIDER_HIDDEN_DP * density) - content.getPaddingTop();
        spiderGroup.setLayoutParams(lp);
    }

    /** Двигает весь мир на s пикселей вправо и на s * SLOPE вниз. Приведение остаётся на месте. */
    private void applyShift(float s) {
        float sy = s * SLOPE;

        wall.setOffset(s, sy);

        for (int i = 0; i < paintings.size(); i++) {
            paintings.get(i).setTranslationX(paintBaseX[i] + s);
            paintings.get(i).setTranslationY(paintBaseY[i] + sy);
        }
        pillar.setTranslationX(pillarBaseX + s);
        pillar.setTranslationY(pillarBaseY + sy);

        // Лестница одинакова через каждые 'period' пикселей, поэтому достаточно сдвига по кругу
        float shift = s % period;
        stairs.setTranslationX(stairsBaseX + shift);
        stairs.setTranslationY(stairsBaseY + shift * SLOPE);

        // Лестница заканчивается у столба: всё, что левее его правого края, обрезаем
        float pillarRightOnScreen = pillar.getLeft() + pillar.getTranslationX()
                + PILLAR_RIGHT_EDGE * pillar.getWidth();
        float stairsLeftOnScreen = stairs.getLeft() + stairs.getTranslationX();
        clipRect.set((int) (pillarRightOnScreen - stairsLeftOnScreen), 0,
                stairs.getWidth(), stairs.getHeight());
        stairs.setClipBounds(clipRect);

        // Шаги: плавная волна, на каждой ступеньке приведение один раз приподнимается и опускается
        float phase = s / period;
        walkBob = STEP_BOB_DP * density * 0.5f * (1f - (float) Math.cos(2 * Math.PI * phase));
        walkTilt = STEP_TILT_DEG * (float) Math.sin(2 * Math.PI * phase);
        updateGhost();
    }

    private void updateGhost() {
        ghost.setTranslationY(ghostBaseY - walkBob - hoverOffset);
        ghost.setRotation(walkTilt);
    }

    //  Бесконечные анимации
    /** Приведение парит (и когда стоит, и когда идёт). */
    private void startHover() {
        hoverAnimator = ValueAnimator.ofFloat(0f, 1f);
        hoverAnimator.setDuration(HOVER_PERIOD_MS / 2);
        hoverAnimator.setRepeatCount(ValueAnimator.INFINITE);
        hoverAnimator.setRepeatMode(ValueAnimator.REVERSE);
        hoverAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        hoverAnimator.addUpdateListener(a -> {
            hoverOffset = (float) a.getAnimatedValue() * HOVER_DP * density;
            updateGhost();
        });
        hoverAnimator.start();
    }

    //  Нажатие на кнопку
    private void onRandomClick() {
        if (isBusy) return;
        isBusy = true;

        if (stepsDone < movies.size()) {
            stepsDone++;
            scrollTo(stepsDone * stepDistance, WALK_DURATION_MS,
                    new AccelerateDecelerateInterpolator(), () -> isBusy = false);
        } else {
            stepsDone++;
            scrollTo(stepsDone * stepDistance, CRASH_DURATION_MS,
                    new AccelerateInterpolator(1.3f), this::hitWall);
        }
    }

    //  Анимации сюжета
    /** Плавно сдвигаем мир до значения target, потом вызываем onEnd. */
    private void scrollTo(float target, long duration, TimeInterpolator interpolator,
                          Runnable onEnd) {
        ValueAnimator animator = ValueAnimator.ofFloat(currentShift, target);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> {
            currentShift = (float) a.getAnimatedValue();
            applyShift(currentShift);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onEnd.run();
            }
        });
        animator.start();
    }

    /** Приведение врезается в столб, отскакивает, а сверху падает табличка. */
    private void hitWall() {
        // Парение мешает отскоку
        if (hoverAnimator != null) hoverAnimator.cancel();

        // Встряска экрана при ударе
        ObjectAnimator shake = ObjectAnimator.ofFloat(content, "translationX",
                0f, -14 * density, 14 * density, -9 * density, 9 * density, 0f);
        shake.setDuration(350);
        shake.start();

        // Отскок назад вниз по лестнице (вдоль её наклона)
        ghost.animate()
                .translationXBy(40 * density)
                .translationYBy(40 * density * SLOPE)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::startDizzy)
                .start();

        handler.postDelayed(this::dropSign, 300);
    }

    /** Кружится голова и смена двух кадров со звёздочками. */
    private void startDizzy() {
        ghost.setImageResource(R.drawable.ghost_dizzy1);

        swayAnimator = ObjectAnimator.ofFloat(ghost, "rotation", -10f, 10f);
        swayAnimator.setDuration(450);
        swayAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        swayAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        swayAnimator.start();

        handler.postDelayed(dizzyFrames, 350);
    }

    /** Табличка "Фильмов больше нет" падает сверху с отскоком. */
    private void dropSign() {
        // Стартовая точка - полностью над экраном
        float startY = -(signNoFilms.getBottom() + content.getPaddingTop());
        signNoFilms.setTranslationY(startY);
        signNoFilms.setVisibility(View.VISIBLE);

        signNoFilms.animate()
                .translationY(0f)
                .setDuration(1400)
                .setInterpolator(new BounceInterpolator())
                .start();

        btnRandom.setText(R.string.btn_finished);
        btnRandom.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (swayAnimator != null) swayAnimator.cancel();
        if (hoverAnimator != null) hoverAnimator.cancel();
    }
}
