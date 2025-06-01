package com.yourname.project_ble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

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

    // Розміри гри - ЗМЕНШИЛИ!
    private int screenWidth;
    private int screenHeight;
    private int blockSize = 35; // Зменшили з 80 до 35
    private int numBlocksWide;
    private int numBlocksHigh;

    // Змійки для двох гравців
    private List<Rect> snake1;  // Гравець 1
    private List<Rect> snake2;  // Гравець 2 (якщо увімкнено)
    private int direction1 = 1; // 0-вгору, 1-вправо, 2-вниз, 3-вліво
    private int direction2 = 3; // Початково вліво
    private int score1 = 0;
    private int score2 = 0;

    // Їжа
    private List<Rect> food;
    private Random random;

    // Час
    private long lastMoveTime = 0;
    private long moveInterval = 400; // Швидше

    // Налаштування гри
    private SettingsActivity.GameSettings gameSettings;
    private boolean surfaceReady = false;
    private SnakeGameActivity parentActivity;

    // Джойстики
    private int joystick1X = 512, joystick1Y = 512;
    private int joystick2X = 512, joystick2Y = 512;

    // Кольори змійок
    private int[] snake1Colors = {
            Color.rgb(76, 175, 80),   // Голова - яскраво-зелена
            Color.rgb(129, 199, 132), // Тіло - світло-зелена
            Color.rgb(165, 214, 167)  // Хвіст - дуже світла
    };

    private int[] snake2Colors = {
            Color.rgb(244, 67, 54),   // Голова - яскраво-червона
            Color.rgb(239, 154, 154), // Тіло - світло-червона
            Color.rgb(255, 205, 210)  // Хвіст - дуже світла
    };

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "🟢 GameView Constructor");

        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        paint = new Paint();
        paint.setAntiAlias(true);

        snake1 = new ArrayList<>();
        snake2 = new ArrayList<>();
        food = new ArrayList<>();
        random = new Random();

        if (context instanceof SnakeGameActivity) {
            parentActivity = (SnakeGameActivity) context;
            Log.d(TAG, "✅ Parent activity set");
        }

        // Завантажуємо налаштування
        loadGameSettings();
    }

    private void loadGameSettings() {
        if (parentActivity != null) {
            gameSettings = SettingsActivity.getGameSettings(parentActivity);

            // Встановлюємо швидкість гри
            int speedPercent = gameSettings.gameSpeed;
            moveInterval = 600 - (speedPercent * 4); // 600ms -> 200ms

            // Встановлюємо розмір поля
            int sizePercent = gameSettings.fieldSize;
            blockSize = 25 + (sizePercent / 5); // 25px -> 45px

            Log.d(TAG, "Settings loaded: players=" + gameSettings.playersCount +
                    ", speed=" + moveInterval + "ms, blockSize=" + blockSize + "px");
        }
    }

    public void startGame() {
        Log.d(TAG, "🎮 START GAME called");

        if (!surfaceReady || screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "⚠️ Surface not ready, cannot start game");
            if (parentActivity != null) {
                parentActivity.runOnUiThread(() -> {
                    parentActivity.updateStatus("Зачекайте, поверхня не готова...");
                });
            }
            return;
        }

        // Зупиняємо попередню гру
        stopGame();

        // Перезавантажуємо налаштування
        loadGameSettings();

        // Обчислюємо поле
        numBlocksWide = screenWidth / blockSize;
        numBlocksHigh = screenHeight / blockSize;

        Log.d(TAG, "Game field: " + numBlocksWide + "x" + numBlocksHigh + " blocks");

        // Ініціалізуємо змійок
        initializeSnakes();

        // Створюємо їжу
        spawnFood();

        // Скидаємо рахунок
        score1 = 0;
        score2 = 0;
        lastMoveTime = System.currentTimeMillis();

        if (parentActivity != null) {
            parentActivity.runOnUiThread(() -> {
                parentActivity.updateScore(gameSettings.playersCount == 1 ? score1 : (score1 + score2));
                String status = gameSettings.playersCount == 1 ?
                        "🐍 Гра запущена! Керуй джойстиком" :
                        "🐍🐍 Кооперативна гра! Збирайте їжу разом";
                parentActivity.updateStatus(status);
            });
        }

        // Запускаємо ігровий цикл
        isPlaying = true;
        gameThread = new Thread(this);
        gameThread.start();

        Log.d(TAG, "✅ Game started successfully!");
    }

    private void initializeSnakes() {
        snake1.clear();
        snake2.clear();

        int centerX = numBlocksWide / 2;
        int centerY = numBlocksHigh / 2;

        // Змійка 1 (завжди є)
        snake1.add(new Rect(centerX * blockSize, centerY * blockSize,
                (centerX + 1) * blockSize, (centerY + 1) * blockSize)); // голова
        snake1.add(new Rect((centerX - 1) * blockSize, centerY * blockSize,
                centerX * blockSize, (centerY + 1) * blockSize)); // тіло
        snake1.add(new Rect((centerX - 2) * blockSize, centerY * blockSize,
                (centerX - 1) * blockSize, (centerY + 1) * blockSize)); // хвіст
        direction1 = 1; // вправо

        // Змійка 2 (тільки якщо 2 гравці)
        if (gameSettings != null && gameSettings.playersCount == 2) {
            int startY = centerY + 3; // Нижче
            if (startY >= numBlocksHigh - 1) startY = centerY - 3; // Або вище

            snake2.add(new Rect(centerX * blockSize, startY * blockSize,
                    (centerX + 1) * blockSize, (startY + 1) * blockSize)); // голова
            snake2.add(new Rect((centerX + 1) * blockSize, startY * blockSize,
                    (centerX + 2) * blockSize, (startY + 1) * blockSize)); // тіло
            snake2.add(new Rect((centerX + 2) * blockSize, startY * blockSize,
                    (centerX + 3) * blockSize, (startY + 1) * blockSize)); // хвіст
            direction2 = 3; // вліво

            Log.d(TAG, "Two snakes initialized");
        } else {
            Log.d(TAG, "Single snake initialized");
        }
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
        updateJoystick1(x, y);
    }

    public void updateJoystick1(int x, int y) {
        if (!isPlaying) return;

        joystick1X = x;
        joystick1Y = y;

        int deadZone = 150; // Збільшили мертву зону
        int deltaX = x - 512;
        int deltaY = y - 512;

        if (Math.abs(deltaX) > deadZone || Math.abs(deltaY) > deadZone) {
            int newDirection = direction1;

            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                // Горизонтальний рух
                if (deltaX > 0 && direction1 != 3) {
                    newDirection = 1; // вправо
                } else if (deltaX < 0 && direction1 != 1) {
                    newDirection = 3; // вліво
                }
            } else {
                // Вертикальний рух
                if (deltaY > 0 && direction1 != 0) {
                    newDirection = 2; // вниз
                } else if (deltaY < 0 && direction1 != 2) {
                    newDirection = 0; // вгору
                }
            }

            if (newDirection != direction1) {
                direction1 = newDirection;
                Log.d(TAG, "🕹️ Player 1 direction: " + direction1);
            }
        }
    }

    public void updateJoystick2(int x, int y) {
        if (!isPlaying || gameSettings.playersCount != 2) return;

        joystick2X = x;
        joystick2Y = y;

        int deadZone = 150;
        int deltaX = x - 512;
        int deltaY = y - 512;

        if (Math.abs(deltaX) > deadZone || Math.abs(deltaY) > deadZone) {
            int newDirection = direction2;

            if (Math.abs(deltaX) > Math.abs(deltaY)) {
                if (deltaX > 0 && direction2 != 3) {
                    newDirection = 1; // вправо
                } else if (deltaX < 0 && direction2 != 1) {
                    newDirection = 3; // вліво
                }
            } else {
                if (deltaY > 0 && direction2 != 0) {
                    newDirection = 2; // вниз
                } else if (deltaY < 0 && direction2 != 2) {
                    newDirection = 0; // вгору
                }
            }

            if (newDirection != direction2) {
                direction2 = newDirection;
                Log.d(TAG, "🕹️ Player 2 direction: " + direction2);
            }
        }
    }

    private void spawnFood() {
        if (numBlocksWide <= 0 || numBlocksHigh <= 0) return;

        food.clear();

        // Створюємо більше їжі для кооперативного режиму
        int foodCount = (gameSettings != null && gameSettings.playersCount == 2) ? 3 : 2;

        for (int i = 0; i < foodCount; i++) {
            int x, y;
            boolean validPosition;
            int attempts = 0;

            do {
                x = random.nextInt(numBlocksWide);
                y = random.nextInt(numBlocksHigh);

                Rect testFood = new Rect(x * blockSize, y * blockSize,
                        (x + 1) * blockSize, (y + 1) * blockSize);

                validPosition = true;

                // Перевіряємо змійку 1
                for (Rect segment : snake1) {
                    if (testFood.equals(segment)) {
                        validPosition = false;
                        break;
                    }
                }

                // Перевіряємо змійку 2
                if (validPosition && gameSettings != null && gameSettings.playersCount == 2) {
                    for (Rect segment : snake2) {
                        if (testFood.equals(segment)) {
                            validPosition = false;
                            break;
                        }
                    }
                }

                // Перевіряємо іншу їжу
                if (validPosition) {
                    for (Rect existingFood : food) {
                        if (testFood.equals(existingFood)) {
                            validPosition = false;
                            break;
                        }
                    }
                }

                attempts++;
            } while (!validPosition && attempts < 50);

            if (validPosition) {
                food.add(new Rect(x * blockSize, y * blockSize,
                        (x + 1) * blockSize, (y + 1) * blockSize));
            }
        }

        Log.d(TAG, "🍎 Spawned " + food.size() + " food items");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "🟢 Surface CREATED");
        surfaceReady = true;
        drawWaitingScreen();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "🔄 Surface CHANGED: " + width + "x" + height);

        screenWidth = width;
        screenHeight = height;

        // Перезавантажуємо налаштування при зміні розміру
        loadGameSettings();

        numBlocksWide = screenWidth / blockSize;
        numBlocksHigh = screenHeight / blockSize;
        surfaceReady = true;

        Log.d(TAG, "Updated field: " + numBlocksWide + "x" + numBlocksHigh + " blocks");
        drawWaitingScreen();
    }

    private void drawWaitingScreen() {
        if (!surfaceReady || !surfaceHolder.getSurface().isValid()) {
            return;
        }

        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas == null) return;

            // Градієнт фон
            LinearGradient gradient = new LinearGradient(
                    0, 0, 0, canvas.getHeight(),
                    Color.rgb(30, 30, 30),
                    Color.rgb(50, 50, 50),
                    Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
            canvas.drawPaint(paint);
            paint.setShader(null);

            // Текст
            paint.setColor(Color.WHITE);
            paint.setTextSize(60);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);

            String text = "🎮 Натисни START";
            canvas.drawText(text, canvas.getWidth() / 2, canvas.getHeight() / 2, paint);

            // Підтекст
            paint.setTextSize(30);
            paint.setColor(Color.LTGRAY);
            String subText = "Підключи джойстик в налаштуваннях";
            canvas.drawText(subText, canvas.getWidth() / 2, canvas.getHeight() / 2 + 80, paint);

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
                Thread.sleep(33); // ~30 FPS
            } catch (InterruptedException e) {
                break;
            }
        }

        Log.d(TAG, "🏁 Game loop ended");
    }

    private void update() {
        // Оновлюємо змійку 1
        if (!snake1.isEmpty()) {
            updateSnake(snake1, direction1, 1);
        }

        // Оновлюємо змійку 2 (якщо є)
        if (gameSettings != null && gameSettings.playersCount == 2 && !snake2.isEmpty()) {
            updateSnake(snake2, direction2, 2);
        }
    }

    private void updateSnake(List<Rect> snake, int direction, int playerNumber) {
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
            Log.d(TAG, "💥 Player " + playerNumber + " hit wall!");
            gameOver(playerNumber + " врізався в стіну!");
            return;
        }

        // Перевіряємо зіткнення з собою
        for (Rect segment : snake) {
            if (newHead.equals(segment)) {
                Log.d(TAG, "💥 Player " + playerNumber + " hit self!");
                gameOver("Гравець " + playerNumber + " врізався в себе!");
                return;
            }
        }

        // Перевіряємо зіткнення з іншою змійкою
        List<Rect> otherSnake = (playerNumber == 1) ? snake2 : snake1;
        if (gameSettings.playersCount == 2) {
            for (Rect segment : otherSnake) {
                if (newHead.equals(segment)) {
                    Log.d(TAG, "💥 Player " + playerNumber + " hit other snake!");
                    gameOver("Змійки зіткнулись!");
                    return;
                }
            }
        }

        // Додаємо нову голову
        snake.add(0, newHead);

        // Перевіряємо їжу
        boolean ateFood = false;
        for (int i = food.size() - 1; i >= 0; i--) {
            Rect foodItem = food.get(i);
            if (newHead.equals(foodItem)) {
                food.remove(i);
                ateFood = true;

                if (playerNumber == 1) {
                    score1++;
                } else {
                    score2++;
                }

                Log.d(TAG, "🍎 Player " + playerNumber + " ate food! Score: " +
                        (playerNumber == 1 ? score1 : score2));
                break;
            }
        }

        if (ateFood) {
            // Додаємо нову їжу
            spawnFood();

            // Прискорюємо гру
            if (moveInterval > 150) {
                moveInterval -= 8;
            }

            // Оновлюємо рахунок
            if (parentActivity != null) {
                parentActivity.runOnUiThread(() -> {
                    int totalScore = gameSettings.playersCount == 1 ? score1 : (score1 + score2);
                    parentActivity.updateScore(totalScore);
                });
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
                if (canvas == null) return;

                // Градієнт фон
                LinearGradient backgroundGradient = new LinearGradient(
                        0, 0, 0, canvas.getHeight(),
                        Color.rgb(15, 25, 15),
                        Color.rgb(25, 40, 25),
                        Shader.TileMode.CLAMP
                );
                paint.setShader(backgroundGradient);
                canvas.drawPaint(paint);
                paint.setShader(null);

                // Малюємо сітку
                drawGrid(canvas);

                // Малюємо їжу
                drawFood(canvas);

                // Малюємо змійок
                drawSnake(canvas, snake1, snake1Colors, "🐍1");
                if (gameSettings != null && gameSettings.playersCount == 2) {
                    drawSnake(canvas, snake2, snake2Colors, "🐍2");
                }

                // Малюємо UI
                drawUI(canvas);

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

    private void drawGrid(Canvas canvas) {
        paint.setColor(Color.rgb(0, 80, 0));
        paint.setStrokeWidth(1);
        paint.setStyle(Paint.Style.STROKE);

        // Вертикальні лінії
        for (int i = 0; i <= numBlocksWide; i++) {
            int x = i * blockSize;
            canvas.drawLine(x, 0, x, screenHeight, paint);
        }

        // Горизонтальні лінії
        for (int i = 0; i <= numBlocksHigh; i++) {
            int y = i * blockSize;
            canvas.drawLine(0, y, screenWidth, y, paint);
        }
    }

    private void drawFood(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);

        for (Rect foodItem : food) {
            // Градієнт для їжі
            LinearGradient foodGradient = new LinearGradient(
                    foodItem.left, foodItem.top, foodItem.right, foodItem.bottom,
                    Color.rgb(255, 100, 100),
                    Color.rgb(255, 200, 100),
                    Shader.TileMode.CLAMP
            );
            paint.setShader(foodGradient);

            // Круглі краї
            RectF foodRectF = new RectF(foodItem);
            canvas.drawRoundRect(foodRectF, blockSize/4, blockSize/4, paint);

            paint.setShader(null);

            // Обводка
            paint.setColor(Color.rgb(255, 255, 100));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawRoundRect(foodRectF, blockSize/4, blockSize/4, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawSnake(Canvas canvas, List<Rect> snake, int[] colors, String label) {
        paint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < snake.size(); i++) {
            Rect segment = snake.get(i);

            // Вибираємо колір
            int colorIndex = Math.min(i, colors.length - 1);
            int color = colors[colorIndex];

            // Градієнт для сегмента
            LinearGradient segmentGradient = new LinearGradient(
                    segment.left, segment.top, segment.right, segment.bottom,
                    color,
                    adjustBrightness(color, 0.7f),
                    Shader.TileMode.CLAMP
            );
            paint.setShader(segmentGradient);

            // Круглі краї
            RectF segmentRectF = new RectF(segment);
            float radius = (i == 0) ? blockSize/3 : blockSize/5; // Голова більш кругла
            canvas.drawRoundRect(segmentRectF, radius, radius, paint);

            paint.setShader(null);

            // Обводка
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            canvas.drawRoundRect(segmentRectF, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawUI(Canvas canvas) {
        paint.setColor(Color.WHITE);
        paint.setTextSize(25);
        paint.setTextAlign(Paint.Align.LEFT);

        if (gameSettings != null && gameSettings.playersCount == 2) {
            canvas.drawText("🐍1: " + score1, 15, 35, paint);
            canvas.drawText("🐍2: " + score2, 15, 65, paint);
            canvas.drawText("💎 Разом: " + (score1 + score2), 15, 95, paint);
        } else {
            canvas.drawText("🐍 Рахунок: " + score1, 15, 35, paint);
        }

        canvas.drawText("⚡ Швидкість: " + (700 - moveInterval), 15, screenHeight - 45, paint);
        canvas.drawText("🍎 Їжі: " + food.size(), 15, screenHeight - 15, paint);

        // Джойстик індикатори
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(20);
        canvas.drawText("🕹️1: " + joystick1X + "," + joystick1Y, screenWidth - 15, 30, paint);

        if (gameSettings.playersCount == 2) {
            canvas.drawText("🕹️2: " + joystick2X + "," + joystick2Y, screenWidth - 15, 55, paint);
        }
    }

    private int adjustBrightness(int color, float factor) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        red = Math.round(red * factor);
        green = Math.round(green * factor);
        blue = Math.round(blue * factor);

        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));

        return Color.rgb(red, green, blue);
    }

    private void gameOver(String reason) {
        Log.d(TAG, "💀 GAME OVER! Reason: " + reason);
        isPlaying = false;

        if (parentActivity != null) {
            parentActivity.runOnUiThread(() -> {
                String message = reason + " Загальний рахунок: ";
                if (gameSettings.playersCount == 2) {
                    message += "🐍1:" + score1 + " 🐍2:" + score2 + " 💎Разом:" + (score1 + score2);
                } else {
                    message += score1;
                }
                parentActivity.updateStatus("💀 " + message + " - Натисни START для нової гри");

                android.widget.Toast.makeText(parentActivity,
                        "Game Over! " + message,
                        android.widget.Toast.LENGTH_LONG).show();
            });
        }
    }
}