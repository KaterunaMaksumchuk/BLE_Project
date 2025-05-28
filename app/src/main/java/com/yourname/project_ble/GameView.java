package com.yourname.project_ble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final String TAG = "GameView";

    private SurfaceHolder surfaceHolder;
    private Thread gameThread;
    private boolean isPlaying = false;
    private Paint paint;
    private final Object drawLock = new Object();

    // Розміри гри
    private int screenWidth;
    private int screenHeight;
    private int blockSize = 80; // Зменшили для кращого відображення
    private int numBlocksWide;
    private int numBlocksHigh;

    // Змійка
    private List<Rect> snake;
    private int direction = 1; // 0-вгору, 1-вправо, 2-вниз, 3-вліво
    private int score = 0;

    // Їжа
    private Rect food;
    private Random random;

    // Час
    private long lastMoveTime = 0;
    private long moveInterval = 600; // Повільніше для початку

    // Посилання на Activity
    private SnakeGameActivity parentActivity;
    private boolean surfaceReady = false;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "🟢 GameView Constructor");

        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        paint = new Paint();
        paint.setAntiAlias(true);
        snake = new ArrayList<>();
        random = new Random();

        if (context instanceof SnakeGameActivity) {
            parentActivity = (SnakeGameActivity) context;
            Log.d(TAG, "✅ Parent activity set");
        }
    }

    public void startGame() {
        Log.d(TAG, "🎮 START GAME called");
        Log.d(TAG, "Surface ready: " + surfaceReady);
        Log.d(TAG, "Screen size: " + screenWidth + "x" + screenHeight);

        if (!surfaceReady || screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "⚠️ Surface not ready, cannot start game");
            if (parentActivity != null) {
                parentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        parentActivity.updateStatus("Зачекайте, поверхня не готова...");
                    }
                });
            }
            return;
        }

        // Зупиняємо попередню гру якщо була
        stopGame();

        // Обчислюємо поле
        numBlocksWide = screenWidth / blockSize;
        numBlocksHigh = screenHeight / blockSize;

        Log.d(TAG, "Game field: " + numBlocksWide + "x" + numBlocksHigh + " blocks");

        // Ініціалізуємо гру
        snake.clear();

        // Створюємо змійку в центрі
        int centerX = numBlocksWide / 2;
        int centerY = numBlocksHigh / 2;

        snake.add(new Rect(centerX * blockSize, centerY * blockSize,
                (centerX + 1) * blockSize, (centerY + 1) * blockSize)); // голова
        snake.add(new Rect((centerX - 1) * blockSize, centerY * blockSize,
                centerX * blockSize, (centerY + 1) * blockSize)); // тіло

        direction = 1; // вправо
        score = 0;
        lastMoveTime = System.currentTimeMillis();

        Log.d(TAG, "Snake created: " + snake.size() + " segments");
        Log.d(TAG, "Head at: " + snake.get(0).toString());

        spawnFood();

        if (parentActivity != null) {
            parentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    parentActivity.updateScore(score);
                    parentActivity.updateStatus("Гра запущена! Керуй джойстиком");
                }
            });
        }

        // Запускаємо ігровий цикл
        isPlaying = true;
        gameThread = new Thread(this);
        gameThread.start();

        Log.d(TAG, "✅ Game started successfully!");
    }

    public void stopGame() {
        Log.d(TAG, "🛑 Stopping game");
        isPlaying = false;

        if (gameThread != null && gameThread.isAlive()) {
            try {
                gameThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping thread", e);
            }
        }
    }

    public void updateJoystick(int x, int y) {
        if (!isPlaying) return;

        int deadZone = 100;
        int deltaX = x - 512;
        int deltaY = y - 512;

        if (Math.abs(deltaX) > deadZone || Math.abs(deltaY) > deadZone) {
            int newDirection = direction;

            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                // Горизонтальний рух
                if (deltaX > 0 && direction != 3) {
                    newDirection = 1; // вправо
                } else if (deltaX < 0 && direction != 1) {
                    newDirection = 3; // вліво
                }
            } else {
                // Вертикальний рух
                if (deltaY > 0 && direction != 0) {
                    newDirection = 2; // вниз
                } else if (deltaY < 0 && direction != 2) {
                    newDirection = 0; // вгору
                }
            }

            if (newDirection != direction) {
                direction = newDirection;
                Log.d(TAG, "🕹️ Direction changed to: " + direction);
            }
        }
    }

    private void spawnFood() {
        if (numBlocksWide <= 0 || numBlocksHigh <= 0) return;

        int x, y;
        boolean validPosition;

        do {
            x = random.nextInt(numBlocksWide);
            y = random.nextInt(numBlocksHigh);

            Rect testFood = new Rect(x * blockSize, y * blockSize,
                    (x + 1) * blockSize, (y + 1) * blockSize);

            validPosition = true;
            for (Rect segment : snake) {
                if (testFood.equals(segment)) {
                    validPosition = false;
                    break;
                }
            }
        } while (!validPosition);

        food = new Rect(x * blockSize, y * blockSize,
                (x + 1) * blockSize, (y + 1) * blockSize);

        Log.d(TAG, "🍎 Food spawned at: " + x + "," + y);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "🟢 Surface CREATED");
        surfaceReady = true;

        // Малюємо тестовий екран одразу
        drawWaitingScreen();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "🔄 Surface CHANGED: " + width + "x" + height);

        screenWidth = width;
        screenHeight = height;
        numBlocksWide = screenWidth / blockSize;
        numBlocksHigh = screenHeight / blockSize;

        surfaceReady = true;

        Log.d(TAG, "Updated field: " + numBlocksWide + "x" + numBlocksHigh + " blocks");

        // Малюємо екран очікування після зміни розміру
        drawWaitingScreen();
    }

    private void drawWaitingScreen() {
        if (!surfaceReady || !surfaceHolder.getSurface().isValid()) {
            return;
        }

        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();

            if (canvas == null) {
                return;
            }

            // Малюємо синій фон замість червоного
            canvas.drawColor(Color.BLUE);

            // Білий текст
            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            paint.setStyle(Paint.Style.FILL);

            String text = "Натисни START";
            canvas.drawText(text, 50, canvas.getHeight() / 2, paint);

            Log.d(TAG, "✅ Waiting screen drawn");

        } catch (Exception e) {
            Log.e(TAG, "Error drawing waiting screen: " + e.getMessage());
        } finally {
            if (canvas != null) {
                try {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                } catch (Exception e) {
                    Log.e(TAG, "Error unlocking waiting canvas: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "🔴 Surface DESTROYED");
        surfaceReady = false;
        stopGame();
    }

    @Override
    public void run() {
        Log.d(TAG, "🔄 Game loop started");

        while (isPlaying && surfaceReady) {
            long currentTime = System.currentTimeMillis();

            // Оновлюємо гру
            if (currentTime - lastMoveTime > moveInterval) {
                update();
                lastMoveTime = currentTime;
            }

            // Малюємо
            draw();

            try {
                Thread.sleep(50); // ~20 FPS
            } catch (InterruptedException e) {
                break;
            }
        }

        Log.d(TAG, "🏁 Game loop ended");
    }

    private void update() {
        if (snake.isEmpty()) return;

        // Рухаємо голову
        Rect newHead = new Rect(snake.get(0));

        switch (direction) {
            case 0: newHead.offset(0, -blockSize); break; // вгору
            case 1: newHead.offset(blockSize, 0); break;  // вправо
            case 2: newHead.offset(0, blockSize); break;  // вниз
            case 3: newHead.offset(-blockSize, 0); break; // вліво
        }

        // Перевіряємо межі
        if (newHead.left < 0 || newHead.right > screenWidth ||
                newHead.top < 0 || newHead.bottom > screenHeight) {
            Log.d(TAG, "💥 Hit wall!");
            gameOver();
            return;
        }

        // Перевіряємо зіткнення з собою
        for (Rect segment : snake) {
            if (newHead.equals(segment)) {
                Log.d(TAG, "💥 Hit self!");
                gameOver();
                return;
            }
        }

        // Додаємо нову голову
        snake.add(0, newHead);

        // Перевіряємо їжу
        if (food != null && newHead.equals(food)) {
            score++;
            Log.d(TAG, "🍎 Food eaten! Score: " + score);

            if (parentActivity != null) {
                parentActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        parentActivity.updateScore(score);
                    }
                });
            }

            spawnFood();

            // Прискорюємо гру
            if (moveInterval > 200) {
                moveInterval -= 15;
            }
        } else {
            // Видаляємо хвіст
            if (snake.size() > 0) {
                snake.remove(snake.size() - 1);
            }
        }
    }

    private void draw() {
        synchronized (drawLock) {
            if (!surfaceReady || !surfaceHolder.getSurface().isValid()) {
                return;
            }

            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();

                if (canvas == null) {
                    return;
                }

                // Очищуємо екран - темно-зелений фон
                canvas.drawColor(Color.rgb(0, 50, 0));

                // Малюємо сітку
                paint.setColor(Color.rgb(0, 100, 0));
                paint.setStrokeWidth(1);
                paint.setStyle(Paint.Style.STROKE);

                for (int i = 0; i <= numBlocksWide; i++) {
                    int x = i * blockSize;
                    canvas.drawLine(x, 0, x, screenHeight, paint);
                }
                for (int i = 0; i <= numBlocksHigh; i++) {
                    int y = i * blockSize;
                    canvas.drawLine(0, y, screenWidth, y, paint);
                }

                // Малюємо змійку
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < snake.size(); i++) {
                    Rect segment = snake.get(i);

                    if (i == 0) {
                        // Голова - яскраво-зелена
                        paint.setColor(Color.rgb(0, 255, 0));
                    } else {
                        // Тіло - світло-зелене
                        paint.setColor(Color.rgb(100, 255, 100));
                    }

                    canvas.drawRect(segment, paint);

                    // Обводка
                    paint.setColor(Color.BLACK);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2);
                    canvas.drawRect(segment, paint);
                    paint.setStyle(Paint.Style.FILL);
                }

                // Малюємо їжу
                if (food != null) {
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(food, paint);

                    // Обводка їжі
                    paint.setColor(Color.YELLOW);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3);
                    canvas.drawRect(food, paint);
                    paint.setStyle(Paint.Style.FILL);
                }

                // Інформація зверху
                paint.setColor(Color.WHITE);
                paint.setTextSize(30);
                canvas.drawText("Score: " + score, 10, 40, paint);
                canvas.drawText("Speed: " + (700 - moveInterval), 10, 80, paint);

            } catch (Exception e) {
                Log.e(TAG, "Error drawing: " + e.getMessage());
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking: " + e.getMessage());
                    }
                }
            }
        }
    }

    private void gameOver() {
        Log.d(TAG, "💀 GAME OVER! Score: " + score);
        isPlaying = false;

        if (parentActivity != null) {
            parentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    parentActivity.updateStatus("Game Over! Рахунок: " + score + " - Натисни START для нової гри");
                    Toast.makeText(parentActivity, "Game Over! Рахунок: " + score, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}