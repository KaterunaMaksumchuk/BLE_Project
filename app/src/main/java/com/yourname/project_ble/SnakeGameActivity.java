package com.yourname.project_ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import android.widget.FrameLayout;

public class SnakeGameActivity extends AppCompatActivity {

    private static final String TAG = "SnakeGame";
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Nordic UART Service UUIDs (як в MainActivity)
    private static final String SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    // UI Elements
    private Button connectButton;
    private Button startGameButton;
    private Button menuButton;
    private TextView statusLabel;
    private TextView scoreLabel;
    //private GameView gameView;
    private com.yourname.project_ble.GameView gameView;

    // BLE Objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic targetCharacteristic;

    // Game State
    private boolean isConnected = false;
    private boolean isScanning = false;
    private Handler handler;

    // Joystick values
    private int joystickX = 512; // центр
    private int joystickY = 512; // центр

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snake_game);

        handler = new Handler(Looper.getMainLooper());

        initializeUI();
        initializeBluetooth();
        checkPermissions();

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

//    private void initializeUI() {
//        connectButton = findViewById(R.id.connectButton);
//        startGameButton = findViewById(R.id.startGameButton);
//        menuButton = findViewById(R.id.menuButton);
//        statusLabel = findViewById(R.id.statusLabel);
//        scoreLabel = findViewById(R.id.scoreLabel);
//        gameView = findViewById(R.id.gameView);
//
//        connectButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (!isConnected && !isScanning) {
//                    startScanAndConnect();
//                } else if (isConnected) {
//                    disconnect();
//                }
//            }
//        });
//
//        startGameButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // Запускаємо гру навіть без джойстика
//                gameView.startGame();
//                updateStatus("Гра запущена! Використовуй джойстик для керування");
//            }
//        });
//
//        startGameButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Log.d(TAG, "🎮 START GAME button clicked!");
//
//                if (gameView == null) {
//                    Log.e(TAG, "❌ gameView is null!");
//                    updateStatus("Помилка: gameView не знайдено");
//                    return;
//                }
//
//                Log.d(TAG, "✅ gameView found, calling startGame()");
//                updateStatus("Запускаю гру...");
//
//                try {
//                    gameView.startGame();
//                    updateStatus("Гра запущена! Використовуй джойстик для керування");
//                } catch (Exception e) {
//                    Log.e(TAG, "❌ Error starting game: " + e.getMessage(), e);
//                    updateStatus("Помилка запуску гри: " + e.getMessage());
//                }
//            }
//        });
//
//        menuButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(SnakeGameActivity.this, MenuActivity.class);
//                startActivity(intent);
//                finish();
//            }
//        });
//    }
    // Замініть метод initializeUI() в SnakeGameActivity.java:
    private void initializeUI() {
        Log.d(TAG, "🎯 initializeUI() started");

        // Знаходимо всі елементи
        connectButton = findViewById(R.id.connectButton);
        startGameButton = findViewById(R.id.startGameButton);
        menuButton = findViewById(R.id.menuButton);
        statusLabel = findViewById(R.id.statusLabel);
        scoreLabel = findViewById(R.id.scoreLabel);
        gameView = findViewById(R.id.gameView);

        // Логуємо що знайшли
        Log.d(TAG, "connectButton: " + (connectButton != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "startGameButton: " + (startGameButton != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "menuButton: " + (menuButton != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "statusLabel: " + (statusLabel != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "scoreLabel: " + (scoreLabel != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));
        Log.d(TAG, "gameView: " + (gameView != null ? "ЗНАЙДЕНО" : "НЕ ЗНАЙДЕНО"));

        if (gameView != null) {
            Log.d(TAG, "✅ GameView class: " + gameView.getClass().getName());
            Log.d(TAG, "✅ GameView size: " + gameView.getWidth() + "x" + gameView.getHeight());
            Log.d(TAG, "✅ GameView visible: " + (gameView.getVisibility() == View.VISIBLE));
        } else {
            Log.e(TAG, "❌❌❌ GAMEVIEW НЕ ЗНАЙДЕНО! Перевірте XML!");
        }

        // Налаштовуємо кнопки
        if (connectButton != null) {
            connectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "🔌 Connect clicked");
                    if (!isConnected && !isScanning) {
                        startScanAndConnect();
                    } else if (isConnected) {
                        disconnect();
                    }
                }
            });
        }

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

                    Log.d(TAG, "✅ Calling gameView.startGame()");
                    updateStatus("Запускаю тест...");
                    gameView.startGame();
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

    // BLE методи (копіюємо з MainActivity)
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth не увімкнений", Toast.LENGTH_SHORT).show();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    private boolean hasRequiredPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= 31) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void checkPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= 31) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        boolean needsPermissions = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermissions = true;
                break;
            }
        }

        if (needsPermissions) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void startScanAndConnect() {
        if (!hasRequiredPermissions()) {
            updateStatus("Потрібні дозволи для сканування");
            checkPermissions();
            return;
        }

        if (bluetoothLeScanner == null) {
            updateStatus("Bluetooth не готовий");
            return;
        }

        isScanning = true;
        connectButton.setEnabled(false);
        updateStatus("Сканування пристроїв...");

        try {
            bluetoothLeScanner.startScan(scanCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при сканування: " + e.getMessage());
            updateStatus("Помилка дозволів");
            isScanning = false;
            connectButton.setEnabled(true);
            return;
        }

        // Зупинити сканування через 10 секунд
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    try {
                        bluetoothLeScanner.stopScan(scanCallback);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException при зупинці: " + e.getMessage());
                    }
                    isScanning = false;
                    connectButton.setEnabled(true);
                    updateStatus("Таймаут - пристрій не знайдено");
                }
            }
        }, 10000L);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            try {
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();

                Log.d(TAG, "Знайдено: " + deviceName + " (" + deviceAddress + ")");

                if (deviceName != null) {
                    Log.d(TAG, "Підключаємося до: " + deviceName);
                    try {
                        bluetoothLeScanner.stopScan(this);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException при зупинці scan: " + e.getMessage());
                    }
                    isScanning = false;
                    connectToDevice(device);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException в scanCallback: " + e.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Помилка сканування: " + errorCode);
            isScanning = false;
            connectButton.setEnabled(true);
            updateStatus("Помилка сканування");
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            updateStatus("Немає дозволів для підключення");
            return;
        }

        try {
            String deviceName = device.getName() != null ? device.getName() : "невідомий пристрій";
            updateStatus("Підключення до " + deviceName + "...");
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при підключенні: " + e.getMessage());
            updateStatus("Помилка дозволів при підключенні");
        }
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(TAG, "Підключено!");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Підключено! Пошук сервісів...");
                        }
                    });
                    try {
                        gatt.discoverServices();
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException при discoverServices: " + e.getMessage());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateStatus("Помилка при пошуку сервісів");
                            }
                        });
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(TAG, "Відключено");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isConnected = false;
                            connectButton.setText("Connect");
                            connectButton.setEnabled(true);
                            updateStatus("Відключено");
                        }
                    });
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Сервіси знайдено");

                try {
                    android.bluetooth.BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                    if (service != null) {
                        Log.d(TAG, "NUS сервіс знайдено!");

                        targetCharacteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                        if (targetCharacteristic != null) {
                            Log.d(TAG, "Характеристика знайдена!");
                            enableNotifications();
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateStatus("Характеристика не знайдена");
                                }
                            });
                        }
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateStatus("NUS сервіс не знайдено");
                            }
                        });
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException при роботі з сервісами: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Помилка доступу до сервісів");
                        }
                    });
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                String dataString = new String(data);
                Log.d(TAG, "Дані: " + dataString);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        parseJoystickData(dataString);
                    }
                });
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Дескриптор записано успішно");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isConnected = true;
                        connectButton.setText("Disconnect");
                        connectButton.setEnabled(true);
                        updateStatus("✅ Готово - грайте!");
                        gameView.startGame(); // Запускаємо гру!
                    }
                });
            } else {
                Log.e(TAG, "Помилка запису дескриптора: " + status);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("Помилка підписки на дані");
                    }
                });
            }
        }
    };

    private void enableNotifications() {
        if (bluetoothGatt == null || targetCharacteristic == null) {
            return;
        }

        try {
            boolean success = bluetoothGatt.setCharacteristicNotification(targetCharacteristic, true);
            if (!success) {
                Log.e(TAG, "Не вдалося увімкнути сповіщення");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("Помилка увімкнення сповіщень");
                    }
                });
                return;
            }

            BluetoothGattDescriptor descriptor = targetCharacteristic.getDescriptor(UUID.fromString(DESCRIPTOR_UUID));
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean writeSuccess = bluetoothGatt.writeDescriptor(descriptor);

                if (!writeSuccess) {
                    Log.e(TAG, "Не вдалося записати дескриптор");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Помилка запису дескриптора");
                        }
                    });
                }
            } else {
                Log.e(TAG, "Дескриптор не знайдено");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("Дескриптор не знайдено");
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при enableNotifications: " + e.getMessage());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatus("Помилка дозволів при підписці");
                }
            });
        }
    }

    private void parseJoystickData(String data) {
        try {
            String cleanData = data.trim()
                    .replace("?", "")
                    .replace("\u0000", "")
                    .replaceAll("[^0-9:]", "");

            Log.d(TAG, "Очищені дані: '" + cleanData + "'");

            String[] parts = cleanData.split(":");

            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                joystickX = Integer.parseInt(parts[0]);
                joystickY = Integer.parseInt(parts[1]);

                // Передаємо дані в гру
                gameView.updateJoystick(joystickX, joystickY);

                Log.d(TAG, "✅ X: " + joystickX + ", Y: " + joystickY);
            } else {
                Log.w(TAG, "Невірний формат після очищення: '" + cleanData + "', parts: " + parts.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Помилка парсингу: " + e.getMessage() + " для даних: '" + data + "'");
        }
    }

    private void disconnect() {
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при відключенні: " + e.getMessage());
        }
    }

    public void updateStatus(String status) {
        statusLabel.setText(status);
        Log.d(TAG, "Status: " + status);
    }

    public void updateScore(int score) {
        scoreLabel.setText("Рахунок: " + score);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isScanning) {
            try {
                if (bluetoothLeScanner != null) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException при закритті: " + e.getMessage());
            }
        }
        disconnect();
        if (gameView != null) {
            gameView.stopGame();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "✅ Дозволи надано", Toast.LENGTH_SHORT).show();
                updateStatus("Готовий до сканування");
            } else {
                Toast.makeText(this, "❌ Потрібні всі дозволи для роботи BLE!", Toast.LENGTH_LONG).show();
                updateStatus("Немає необхідних дозволів");
            }
        }
    }
}
