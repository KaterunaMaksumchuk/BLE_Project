package com.yourname.project_ble;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class SnakeGameActivity extends AppCompatActivity {

    private static final String TAG = "SnakeGame";

    // UI Elements
    private Button startGameButton;
    private Button settingsButton;
    private Button menuButton;
    private TextView statusLabel;
    private TextView scoreLabel;
    private GameView gameView;

    // Game State
    private Handler handler;
    private SettingsActivity.GameSettings gameSettings;
    private BLEConnectionService bleService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snake_game);

        handler = new Handler(Looper.getMainLooper());
        bleService = BLEConnectionService.getInstance();

        initializeUI();
        loadGameSettings();
        setupBLEConnection();

        // Обробка кнопки "Назад"
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(SnakeGameActivity.this, MenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
        });
    }

    private void initializeUI() {
        Log.d(TAG, "🎯 initializeUI() started");

        // Знаходимо всі елементи
        startGameButton = findViewById(R.id.startGameButton);
        settingsButton = findViewById(R.id.settingsButton);
        menuButton = findViewById(R.id.menuButton);
        statusLabel = findViewById(R.id.statusLabel);
        scoreLabel = findViewById(R.id.scoreLabel);
        gameView = findViewById(R.id.gameView);

        // Логуємо що знайшли
        Log.d(TAG, "startGameButton: " + (startGameButton != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "settingsButton: " + (settingsButton != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "menuButton: " + (menuButton != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "statusLabel: " + (statusLabel != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "scoreLabel: " + (scoreLabel != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "gameView: " + (gameView != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));

        if (gameView != null) {
            Log.d(TAG, "✅ GameView class: " + gameView.getClass().getName());
        } else {
            Log.e(TAG, "❌❌❌ GAMEVIEW НЕ ЗНАЙДЕНО! Перевірте XML!");
        }

        // Налаштовуємо кнопки
        if (startGameButton != null) {
            startGameButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "🎮 START clicked!");

                    if (gameView == null) {
                        Log.e(TAG, "❌ GameView is NULL!");
                        updateStatus("ПОМИЛКА: GameView не знайдено!");
                        return;
                    }

                    // Перевіряємо підключення контролерів
                    if (!checkControllerConnections()) {
                        return;
                    }

                    Log.d(TAG, "✅ Starting game...");
                    updateStatus("🎮 Запускаю гру...");
                    gameView.startGame();
                }
            });
        }

        if (settingsButton != null) {
            settingsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SnakeGameActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (menuButton != null) {
            menuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SnakeGameActivity.this, MenuActivity.class);
                    startActivity(intent);
                    finish();
                }
            });
        }

        Log.d(TAG, "🎯 initializeUI() completed");
    }

    private void loadGameSettings() {
        gameSettings = SettingsActivity.getGameSettings(this);

        Log.d(TAG, "Loaded settings: players=" + gameSettings.playersCount +
                ", player1Connected=" + gameSettings.player1Connected +
                ", player2Connected=" + gameSettings.player2Connected);

        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        if (gameSettings == null) return;

        StringBuilder status = new StringBuilder();

        if (gameSettings.playersCount == 1) {
            if (gameSettings.player1Connected) {
                status.append("🎮 Контролер підключено");
            } else {
                status.append("❌ Підключи контролер в налаштуваннях");
            }
        } else {
            // Два гравці
            if (gameSettings.player1Connected && gameSettings.player2Connected) {
                status.append("🎮🎮 Обидва контролери підключено");
            } else if (gameSettings.player1Connected) {
                status.append("🎮❌ Тільки гравець 1 підключено");
            } else if (gameSettings.player2Connected) {
                status.append("❌🎮 Тільки гравець 2 підключено");
            } else {
                status.append("❌❌ Підключи контролери в налаштуваннях");
            }
        }

        updateStatus(status.toString());
    }

    private boolean checkControllerConnections() {
        if (gameSettings == null) {
            updateStatus("❌ Помилка завантаження налаштувань");
            return false;
        }

        // Перевіряємо фактичний стан BLE підключень через Service
        boolean actualPlayer1 = bleService.isPlayer1Connected();

        // ✅ ВИПРАВЛЕННЯ: Для одного пристрою з двома джойстиками
        // Player 2 використовує ту ж BLE характеристику що і Player 1
        boolean actualPlayer2 = actualPlayer1; // Той же пристрій!

        Log.d(TAG, "🔍 Checking connections - P1: " + actualPlayer1 + ", P2: " + actualPlayer2);

        if (gameSettings.playersCount == 1) {
            if (!actualPlayer1) {
                updateStatus("❌ Контролер гравця 1 не підключений - перевірте налаштування");
                Toast.makeText(this, "Спочатку підключи контролер в налаштуваннях!", Toast.LENGTH_LONG).show();
                return false;
            }
        } else {
            // Для 2 гравців достатньо одного BLE підключення (один пристрій - два джойстики)
            if (!actualPlayer1) {
                updateStatus("❌ Контролер не підключений - перевірте налаштування");
                Toast.makeText(this, "Підключи контролер в налаштуваннях!", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        return true;
    }

    private void setupBLEConnection() {
        Log.d(TAG, "🔧 Setting up BLE connection...");

        if (gameSettings == null) {
            updateStatus("❌ Налаштування не завантажені");
            return;
        }

        // Встановлюємо слухач для даних джойстиків
        bleService.setJoystickDataListener(new BLEConnectionService.JoystickDataListener() {
            @Override
            public void onPlayer1Data(int x, int y) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gameView != null) {
                            Log.d(TAG, "🕹️ P1 Joystick: " + x + "," + y);
                            gameView.updateJoystick1(x, y);
                        }
                    }
                });
            }

            @Override
            public void onPlayer2Data(int x, int y) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gameView != null && gameSettings.playersCount == 2) {
                            Log.d(TAG, "🕹️ P2 Joystick: " + x + "," + y);
                            gameView.updateJoystick2(x, y);
                        }
                    }
                });
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("BLE: " + status);
                    }
                });
            }
        });

        // Логуємо стан підключень
        bleService.logStatus();

        // ✅ ВИПРАВЛЕННЯ: Правильна перевірка для одного пристрою
        boolean actualPlayer1 = bleService.isPlayer1Connected();
        boolean actualPlayer2 = actualPlayer1; // Той же пристрій!

        Log.d(TAG, "Settings P1: " + gameSettings.player1Connected + ", Actual P1: " + actualPlayer1);
        Log.d(TAG, "Settings P2: " + gameSettings.player2Connected + ", Actual P2: " + actualPlayer2);

        // Оновлюємо статус підключення
        String statusMessage;
        if (gameSettings.playersCount == 1) {
            if (actualPlayer1) {
                statusMessage = "🎮 Контролер готовий";
            } else {
                statusMessage = "❌ Підключи контролер в налаштуваннях";
            }
        } else {
            if (actualPlayer1) { // Достатньо одного підключення
                statusMessage = "🎮🎮 Обидва джойстики готові";
            } else {
                statusMessage = "❌❌ Підключи контролер в налаштуваннях";
            }
        }

        updateStatus(statusMessage);
    }

    public void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
        Log.d(TAG, "Status: " + status);
    }

    public void updateScore(int score) {
        if (scoreLabel != null) {
            scoreLabel.setText("Рахунок: " + score);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Перезавантажуємо налаштування при поверненні з Settings
        loadGameSettings();
        setupBLEConnection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Очищуємо слухач BLE Service
        if (bleService != null) {
            bleService.setJoystickDataListener(null);
        }

        if (gameView != null) {
            gameView.stopGame();
        }
    }
}