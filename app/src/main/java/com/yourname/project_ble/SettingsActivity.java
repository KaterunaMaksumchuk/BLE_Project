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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Константа для MAC-адреси джойстика (замініть на вашу реальну MAC-адресу)
    private static final String JOYSTICK_MAC_ADDRESS = "FF:CD:2D:EA:F8:7B"; // Ваша MAC-адреса з логів

    // SharedPreferences ключі
    private static final String PREFS_NAME = "SnakeGamePrefs";
    private static final String KEY_PLAYERS_COUNT = "players_count";
    private static final String KEY_GAME_SPEED = "game_speed";
    private static final String KEY_FIELD_SIZE = "field_size";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_SOUND_EFFECTS = "sound_effects";
    private static final String KEY_PLAYER1_CONNECTED = "player1_connected";
    private static final String KEY_PLAYER2_CONNECTED = "player2_connected";

    // BLE константи
    private static final String SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String CHARACTERISTIC_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    // UI Elements
    private ImageButton backButton;
    private Button connectPlayer1Button, connectPlayer2Button;
    private TextView player1Status, player2Status, connectionStatus;
    private RadioGroup playersRadioGroup;
    private RadioButton onePlayerRadio, twoPlayersRadio;
    private SeekBar speedSeekBar, fieldSizeSeekBar, volumeSeekBar;
    private TextView speedValue, fieldSizeValue, volumeValue;
    private Switch soundEffectsSwitch;
    private Button resetButton, saveButton;
    private android.view.View player2Section;

    // BLE Objects
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt player1Gatt, player2Gatt;
    private BluetoothGattCharacteristic player1Characteristic, player2Characteristic;

    // Connection states
    private boolean player1Connected = false;
    private boolean player2Connected = false;
    private boolean isScanning = false;
    private int connectingPlayer = 0;

    private Handler handler;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initializeUI();
        initializeBluetooth();
        loadSettings();
        setupListeners();
        checkPermissions();
        setupJoystickTesting();

        // Обробка кнопки "Назад"
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveSettings();
                finish();
            }
        });
    }

    /**
     * Спроба прямого підключення до джойстика за MAC-адресою
     */
    private boolean tryDirectConnection() {
        try {
            Log.d(TAG, "🎯 Спроба прямого підключення до: " + JOYSTICK_MAC_ADDRESS);
            updateConnectionStatus("🔗 Пряме підключення до джойстика...");

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(JOYSTICK_MAC_ADDRESS);
            if (device != null) {
                connectToDevice(device);
                return true;
            } else {
                Log.w(TAG, "⚠️ Не вдалося отримати пристрій за MAC-адресою");
                return false;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "❌ Невірна MAC-адреса: " + e.getMessage());
            updateConnectionStatus("❌ Невірна MAC-адреса джойстика");
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Немає дозволів для прямого підключення: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "❌ Помилка прямого підключення: " + e.getMessage());
            return false;
        }
    }

    private void setupJoystickTesting() {
        // Встановлюємо тестовий слухач джойстика
        BLEConnectionService bleService = BLEConnectionService.getInstance();
        bleService.setJoystickDataListener(new BLEConnectionService.JoystickDataListener() {
            @Override
            public void onPlayer1Data(int x, int y) {
                runOnUiThread(() -> {
                    updateConnectionStatus("🕹️ Гравець 1: X=" + x + " Y=" + y);
                });
            }

            @Override
            public void onPlayer2Data(int x, int y) {
                runOnUiThread(() -> {
                    updateConnectionStatus("🕹️ Гравець 2: X=" + x + " Y=" + y);
                });
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                runOnUiThread(() -> {
                    updateConnectionStatus(status);
                });
            }
        });
    }

    private void initializeUI() {
        backButton = findViewById(R.id.backButton);
        connectPlayer1Button = findViewById(R.id.connectPlayer1Button);
        connectPlayer2Button = findViewById(R.id.connectPlayer2Button);
        player1Status = findViewById(R.id.player1Status);
        player2Status = findViewById(R.id.player2Status);
        connectionStatus = findViewById(R.id.connectionStatus);

        playersRadioGroup = findViewById(R.id.playersRadioGroup);
        onePlayerRadio = findViewById(R.id.onePlayerRadio);
        twoPlayersRadio = findViewById(R.id.twoPlayersRadio);
        player2Section = findViewById(R.id.player2Section);

        speedSeekBar = findViewById(R.id.speedSeekBar);
        fieldSizeSeekBar = findViewById(R.id.fieldSizeSeekBar);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);

        speedValue = findViewById(R.id.speedValue);
        fieldSizeValue = findViewById(R.id.fieldSizeValue);
        volumeValue = findViewById(R.id.volumeValue);

        soundEffectsSwitch = findViewById(R.id.soundEffectsSwitch);
        resetButton = findViewById(R.id.resetButton);
        saveButton = findViewById(R.id.saveButton);
    }

    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            updateConnectionStatus("❌ Bluetooth не увімкнений");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        updateConnectionStatus("✅ Bluetooth готовий");
    }

    private void loadSettings() {
        int playersCount = prefs.getInt(KEY_PLAYERS_COUNT, 1);
        int gameSpeed = prefs.getInt(KEY_GAME_SPEED, 50);
        int fieldSize = prefs.getInt(KEY_FIELD_SIZE, 50);
        int volume = prefs.getInt(KEY_VOLUME, 50);
        boolean soundEffects = prefs.getBoolean(KEY_SOUND_EFFECTS, true);
        player1Connected = prefs.getBoolean(KEY_PLAYER1_CONNECTED, false);
        player2Connected = prefs.getBoolean(KEY_PLAYER2_CONNECTED, false);

        if (playersCount == 1) {
            onePlayerRadio.setChecked(true);
        } else {
            twoPlayersRadio.setChecked(true);
        }

        speedSeekBar.setProgress(gameSpeed);
        fieldSizeSeekBar.setProgress(fieldSize);
        volumeSeekBar.setProgress(volume);
        soundEffectsSwitch.setChecked(soundEffects);

        updateSpeedText(gameSpeed);
        updateFieldSizeText(fieldSize);
        updateVolumeText(volume);
        updatePlayersUI(playersCount);
        updateConnectionUI();
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> {
            saveSettings();
            finish();
        });

        connectPlayer1Button.setOnClickListener(v -> {
            if (player1Connected) {
                disconnectPlayer1();
            } else {
                connectingPlayer = 1;
                // Спробуємо спершу пряме підключення, потім сканування
                if (!tryDirectConnection()) {
                    startScanAndConnect();
                }
            }
        });

        connectPlayer2Button.setOnClickListener(v -> {
            // Для одного пристрою з двома джойстиками - кнопка неактивна
            Toast.makeText(this, "Обидва джойстики на одному пристрої!\nПідключи через кнопку Гравця 1", Toast.LENGTH_LONG).show();
        });

        playersRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int playersCount = (checkedId == R.id.onePlayerRadio) ? 1 : 2;
            updatePlayersUI(playersCount);
        });

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSpeedText(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        fieldSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFieldSizeText(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeText(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        resetButton.setOnClickListener(v -> resetToDefaults());
        saveButton.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "✅ Налаштування збережено!", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePlayersUI(int playersCount) {
        if (playersCount == 1) {
            // Для 1 гравця - затемнюємо секцію гравця 2
            player2Section.setAlpha(0.5f);
            connectPlayer2Button.setEnabled(false);
            connectPlayer2Button.setText("Недоступно");
        } else {
            // Для 2 гравців - показуємо що це той же пристрій
            player2Section.setAlpha(1.0f);
            connectPlayer2Button.setEnabled(false); // Завжди неактивна!
            connectPlayer2Button.setText("Той же пристрій");

            // Якщо Player 1 підключений, то автоматично Player 2 теж
            if (player1Connected) {
                player2Connected = true;
                updateConnectionUI();
            }
        }

        Log.d(TAG, "Players UI updated for " + playersCount + " players");
    }

    private void updateSpeedText(int progress) {
        String[] speeds = {"Повільна", "Середня", "Швидка"};
        int index = Math.min(progress / 34, 2);
        speedValue.setText(speeds[index]);
    }

    private void updateFieldSizeText(int progress) {
        String[] sizes = {"Мале", "Середнє", "Велике"};
        int index = Math.min(progress / 34, 2);
        fieldSizeValue.setText(sizes[index]);
    }

    private void updateVolumeText(int progress) {
        volumeValue.setText(progress + "%");
    }

    private void updateConnectionUI() {
        if (player1Connected) {
            player1Status.setText("✅ Підключено");
            player1Status.setTextColor(getColor(android.R.color.holo_green_light));
            connectPlayer1Button.setText("Відключити");
        } else {
            player1Status.setText("❌ Відключено");
            player1Status.setTextColor(getColor(android.R.color.holo_red_light));
            connectPlayer1Button.setText("Підключити");
        }

        if (player2Connected) {
            player2Status.setText("✅ Підключено");
            player2Status.setTextColor(getColor(android.R.color.holo_green_light));
            connectPlayer2Button.setText("Відключити");
        } else {
            player2Status.setText("❌ Відключено");
            player2Status.setTextColor(getColor(android.R.color.holo_red_light));
            connectPlayer2Button.setText("Підключити");
        }
    }

    private void updateConnectionStatus(String status) {
        connectionStatus.setText(status);
        Log.d(TAG, "Status: " + status);
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
            updateConnectionStatus("❌ Потрібні дозволи для сканування");
            checkPermissions();
            return;
        }

        if (bluetoothLeScanner == null) {
            updateConnectionStatus("❌ Bluetooth не готовий");
            return;
        }

        if (isScanning) {
            updateConnectionStatus("⏳ Вже сканую...");
            return;
        }

        isScanning = true;
        updateConnectionStatus("🔍 Сканування пристроїв для гравця " + connectingPlayer + "...");

        connectPlayer1Button.setEnabled(false);
        connectPlayer2Button.setEnabled(false);

        try {
            bluetoothLeScanner.startScan(scanCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при скануванні: " + e.getMessage());
            updateConnectionStatus("❌ Помилка дозволів");
            resetScanState();
            return;
        }

        handler.postDelayed(() -> {
            if (isScanning) {
                try {
                    bluetoothLeScanner.stopScan(scanCallback);
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException при зупинці: " + e.getMessage());
                }
                resetScanState();
                updateConnectionStatus("⏰ Джойстик " + JOYSTICK_MAC_ADDRESS + " не знайдено");
            }
        }, 10000L);
    }

    private void resetScanState() {
        isScanning = false;
        connectPlayer1Button.setEnabled(!player1Connected);
        connectPlayer2Button.setEnabled(!player2Connected && twoPlayersRadio.isChecked());
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();

                // Перевіряємо чи це наш джойстик за MAC-адресою або ім'ям
                if (deviceName != null && (deviceName.contains("nRF52") ||
                        deviceName.contains("Nordic") || deviceName.contains("UART") ||
                        JOYSTICK_MAC_ADDRESS.equals(deviceAddress))) {

                    Log.d(TAG, "Знайдено джойстик: " + deviceName + " (" + deviceAddress + ")");
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
            resetScanState();
            updateConnectionStatus("❌ Помилка сканування");
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            updateConnectionStatus("❌ Немає дозволів для підключення");
            resetScanState();
            return;
        }

        try {
            String deviceName = device.getName() != null ? device.getName() : "невідомий пристрій";
            updateConnectionStatus("🔗 Підключення гравця " + connectingPlayer + " до " + deviceName + "...");

            BluetoothGattCallback callback = (connectingPlayer == 1) ? gattCallbackPlayer1 : gattCallbackPlayer2;
            BluetoothGatt gatt = device.connectGatt(this, false, callback);

            if (connectingPlayer == 1) {
                player1Gatt = gatt;
            } else {
                player2Gatt = gatt;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при підключенні: " + e.getMessage());
            updateConnectionStatus("❌ Помилка дозволів при підключенні");
            resetScanState();
        }
    }

    private BluetoothGattCallback gattCallbackPlayer1 = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            handleConnectionStateChange(gatt, status, newState, 1);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            handleServicesDiscovered(gatt, status, 1);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristicChanged(gatt, characteristic, 1);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            handleDescriptorWrite(gatt, descriptor, status, 1);
        }
    };

    private BluetoothGattCallback gattCallbackPlayer2 = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            handleConnectionStateChange(gatt, status, newState, 2);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            handleServicesDiscovered(gatt, status, 2);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristicChanged(gatt, characteristic, 2);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            handleDescriptorWrite(gatt, descriptor, status, 2);
        }
    };

    private void handleConnectionStateChange(BluetoothGatt gatt, int status, int newState, int playerNumber) {
        switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
                runOnUiThread(() -> {
                    updateConnectionStatus("✅ Гравець " + playerNumber + " підключено! Пошук сервісів...");
                });
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException при discoverServices: " + e.getMessage());
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                runOnUiThread(() -> {
                    if (playerNumber == 1) {
                        player1Connected = false;
                    } else {
                        player2Connected = false;
                    }
                    updateConnectionUI();
                    resetScanState();
                    updateConnectionStatus("❌ Гравець " + playerNumber + " відключено");
                });
                break;
        }
    }

    private void handleServicesDiscovered(BluetoothGatt gatt, int status, int playerNumber) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                android.bluetooth.BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                    if (characteristic != null) {
                        if (playerNumber == 1) {
                            player1Characteristic = characteristic;
                        } else {
                            player2Characteristic = characteristic;
                        }
                        enableNotifications(gatt, characteristic, playerNumber);
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException при роботі з сервісами: " + e.getMessage());
            }
        }
    }

    private void handleCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int playerNumber) {
        byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {

            Log.d(TAG, "📦 Received " + data.length + " bytes from JOYSTICK");

            // Логуємо сирі байти для налагодження
            StringBuilder hexString = new StringBuilder();
            for (byte b : data) {
                hexString.append(String.format("%02X ", b & 0xFF));
            }
            Log.d(TAG, "🔢 HEX data: " + hexString.toString());

            // Передаємо дані в BLE Service для обробки
            BLEConnectionService bleService = BLEConnectionService.getInstance();

            // Використовуємо універсальний метод - автоматично визначить формат
            bleService.parseJoystickData(data);
        }
    }

    private void handleDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status, int playerNumber) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BLEConnectionService bleService = BLEConnectionService.getInstance();

            // Для одного пристрою з двома джойстиками
            if (playerNumber == 1) {
                bleService.setPlayer1Connection(gatt, player1Characteristic);

                // Якщо в режимі 2 гравців - автоматично встановлюємо і Player 2
                if (twoPlayersRadio.isChecked()) {
                    bleService.setPlayer2Connection(gatt, player1Characteristic); // Той же пристрій!
                }
            }

            runOnUiThread(() -> {
                if (playerNumber == 1) {
                    player1Connected = true;
                    // Для режиму 2 гравців автоматично підключаємо другого
                    if (twoPlayersRadio.isChecked()) {
                        player2Connected = true;
                    }
                } else {
                    player2Connected = true;
                }
                updateConnectionUI();
                resetScanState();

                String message = "✅ Гравець " + playerNumber + " готовий до гри!";
                if (twoPlayersRadio.isChecked() && playerNumber == 1) {
                    message = "✅ Обидва джойстики готові до гри!";
                }
                updateConnectionStatus(message);
            });
        }
    }

    private void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int playerNumber) {
        try {
            boolean success = gatt.setCharacteristicNotification(characteristic, true);
            if (success) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_UUID));
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при enableNotifications: " + e.getMessage());
        }
    }

    // Додаємо метод для ручного відключення (викликається тільки при явному натисканні "Відключити")
    private void forceDisconnectPlayer1() {
        try {
            if (player1Gatt != null) {
                player1Gatt.disconnect();
                player1Gatt.close();
                player1Gatt = null;
                player1Characteristic = null;
            }
            BLEConnectionService.getInstance().clearPlayer1Connection();
            player1Connected = false;

            // Якщо в режимі 2 гравців, то відключаємо і другого
            if (twoPlayersRadio.isChecked()) {
                player2Connected = false;
                BLEConnectionService.getInstance().clearPlayer2Connection();
            }

            updateConnectionUI();
            updateConnectionStatus("❌ Гравець 1 відключено");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при відключенні гравця 1: " + e.getMessage());
        }
    }

    private void disconnectPlayer1() {
        // Змінюємо на forceDisconnectPlayer1()
        forceDisconnectPlayer1();
    }

    private void disconnectPlayer2() {
        try {
            if (player2Gatt != null) {
                player2Gatt.disconnect();
                player2Gatt.close();
                player2Gatt = null;
                player2Characteristic = null;
            }
            BLEConnectionService.getInstance().clearPlayer2Connection();
            player2Connected = false;
            updateConnectionUI();
            updateConnectionStatus("❌ Гравець 2 відключено");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException при відключенні гравця 2: " + e.getMessage());
        }
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        int playersCount = onePlayerRadio.isChecked() ? 1 : 2;
        editor.putInt(KEY_PLAYERS_COUNT, playersCount);
        editor.putInt(KEY_GAME_SPEED, speedSeekBar.getProgress());
        editor.putInt(KEY_FIELD_SIZE, fieldSizeSeekBar.getProgress());
        editor.putInt(KEY_VOLUME, volumeSeekBar.getProgress());
        editor.putBoolean(KEY_SOUND_EFFECTS, soundEffectsSwitch.isChecked());
        editor.putBoolean(KEY_PLAYER1_CONNECTED, player1Connected);
        editor.putBoolean(KEY_PLAYER2_CONNECTED, player2Connected);
        editor.apply();
    }

    private void resetToDefaults() {
        onePlayerRadio.setChecked(true);
        speedSeekBar.setProgress(50);
        fieldSizeSeekBar.setProgress(50);
        volumeSeekBar.setProgress(50);
        soundEffectsSwitch.setChecked(true);
        updateSpeedText(50);
        updateFieldSizeText(50);
        updateVolumeText(50);
        updatePlayersUI(1);
        Toast.makeText(this, "🔄 Налаштування скинуто", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Зупиняємо сканування якщо активне
        if (isScanning) {
            try {
                if (bluetoothLeScanner != null) {
                    bluetoothLeScanner.stopScan(scanCallback);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException при закритті: " + e.getMessage());
            }
        }

        // ❗ НЕ ВІДКЛЮЧАЄМО джойстики при закритті налаштувань!
        // disconnectPlayer1(); // ВИДАЛЕНО
        // disconnectPlayer2(); // ВИДАЛЕНО

        // Тільки зберігаємо налаштування
        saveSettings();

        Log.d(TAG, "Settings закрито, з'єднання збережено");
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
                updateConnectionStatus("✅ Готовий до сканування");
            } else {
                Toast.makeText(this, "❌ Потрібні всі дозволи для роботи BLE!", Toast.LENGTH_LONG).show();
                updateConnectionStatus("❌ Немає необхідних дозволів");
            }
        }
    }

    // Статичний метод для отримання налаштувань
    public static GameSettings getGameSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new GameSettings(
                prefs.getInt(KEY_PLAYERS_COUNT, 1),
                prefs.getInt(KEY_GAME_SPEED, 50),
                prefs.getInt(KEY_FIELD_SIZE, 50),
                prefs.getInt(KEY_VOLUME, 50),
                prefs.getBoolean(KEY_SOUND_EFFECTS, true),
                prefs.getBoolean(KEY_PLAYER1_CONNECTED, false),
                prefs.getBoolean(KEY_PLAYER2_CONNECTED, false)
        );
    }

    // Внутрішній клас для налаштувань
    public static class GameSettings {
        public final int playersCount;
        public final int gameSpeed;
        public final int fieldSize;
        public final int volume;
        public final boolean soundEffects;
        public final boolean player1Connected;
        public final boolean player2Connected;

        public GameSettings(int playersCount, int gameSpeed, int fieldSize, int volume,
                            boolean soundEffects, boolean player1Connected, boolean player2Connected) {
            this.playersCount = playersCount;
            this.gameSpeed = gameSpeed;
            this.fieldSize = fieldSize;
            this.volume = volume;
            this.soundEffects = soundEffects;
            this.player1Connected = player1Connected;
            this.player2Connected = player2Connected;
        }
    }
}