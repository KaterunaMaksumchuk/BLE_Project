package com.yourname.project_ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

/**
 * Singleton сервіс для управління BLE підключеннями між Activity
 */
public class BLEConnectionService {

    private static final String TAG = "BLEConnectionService";
    private static BLEConnectionService instance;

    // BLE об'єкти
    private BluetoothGatt player1Gatt;
    private BluetoothGatt player2Gatt;
    private BluetoothGattCharacteristic player1Characteristic;
    private BluetoothGattCharacteristic player2Characteristic;

    // Слухачі для даних джойстиків
    private JoystickDataListener joystickListener;

    public interface JoystickDataListener {
        void onPlayer1Data(int x, int y);
        void onPlayer2Data(int x, int y);
        void onConnectionStatusChanged(String status);
    }

    private BLEConnectionService() {
        // Приватний конструктор для Singleton
    }

    public static synchronized BLEConnectionService getInstance() {
        if (instance == null) {
            instance = new BLEConnectionService();
        }
        return instance;
    }

    // Методи для встановлення BLE об'єктів (викликаються з SettingsActivity)
    public void setPlayer1Connection(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        this.player1Gatt = gatt;
        this.player1Characteristic = characteristic;
        Log.d(TAG, "Player 1 connection set");
    }

    public void setPlayer2Connection(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        this.player2Gatt = gatt;
        this.player2Characteristic = characteristic;
        Log.d(TAG, "Player 2 connection set");
    }

    // Методи для отримання BLE об'єктів (викликаються з SnakeGameActivity)
    public BluetoothGatt getPlayer1Gatt() {
        return player1Gatt;
    }

    public BluetoothGatt getPlayer2Gatt() {
        return player2Gatt;
    }

    public BluetoothGattCharacteristic getPlayer1Characteristic() {
        return player1Characteristic;
    }

    public BluetoothGattCharacteristic getPlayer2Characteristic() {
        return player2Characteristic;
    }

    // Методи для обробки даних
    public void setJoystickDataListener(JoystickDataListener listener) {
        this.joystickListener = listener;
    }

    /**
     * ✅ ВИПРАВЛЕНИЙ метод для парсингу 4-байтових даних з правильним порядком
     * @param data - масив з 4 байтів від nRF52832
     *
     * nRF52832 відправляє байти в такому порядку:
     * data[0] = temp4 (val_mv3 - channel 3) = Player 2, Y
     * data[1] = temp3 (val_mv2 - channel 2) = Player 2, X
     * data[2] = temp2 (val_mv1 - channel 1) = Player 1, Y
     * data[3] = temp1 (val_mv0 - channel 0) = Player 1, X
     */
    public void parseBinaryJoystickData(byte[] data) {
        try {
            if (data == null || data.length < 4) {
                Log.w(TAG, "❌ Invalid data length: " + (data != null ? data.length : "null"));
                return;
            }

            // ✅ ПРАВИЛЬНИЙ ПОРЯДОК байтів згідно з nRF52832 main.c:
            int player2_y_raw = data[0] & 0xFF;  // temp4 (val_mv3 - channel 3)
            int player2_x_raw = data[1] & 0xFF;  // temp3 (val_mv2 - channel 2)
            int player1_y_raw = data[2] & 0xFF;  // temp2 (val_mv1 - channel 1)
            int player1_x_raw = data[3] & 0xFF;  // temp1 (val_mv0 - channel 0)

            Log.d(TAG, "🎮 RAW BINARY: P1_X=" + player1_x_raw + " P1_Y=" + player1_y_raw +
                    " P2_X=" + player2_x_raw + " P2_Y=" + player2_y_raw);

            // ✅ НОВА СИСТЕМА КООРДИНАТ: конвертуємо з діапазону 0-255 в 0-1000
            int player1_x = (player1_x_raw * 1000) / 255;
            int player1_y = (player1_y_raw * 1000) / 255;
            int player2_x = (player2_x_raw * 1000) / 255;
            int player2_y = (player2_y_raw * 1000) / 255;

            Log.d(TAG, "✅ SCALED to 0-1000: P1(X=" + player1_x + ", Y=" + player1_y +
                    ") P2(X=" + player2_x + ", Y=" + player2_y + ")");

            // Відправляємо дані слухачам
            if (joystickListener != null) {
                joystickListener.onPlayer1Data(player1_x, player1_y);
                joystickListener.onPlayer2Data(player2_x, player2_y);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Binary parse error: " + e.getMessage());
        }
    }

    /**
     * Універсальний метод для автоматичного визначення формату даних
     */
    public void parseJoystickData(byte[] data) {
        if (data == null || data.length == 0) {
            Log.w(TAG, "❌ Empty data received");
            return;
        }

        Log.d(TAG, "📦 Received " + data.length + " bytes");

        if (data.length == 4) {
            // Новий бінарний формат: 4 байти
            Log.d(TAG, "🎯 Using BINARY format (4 bytes)");
            parseBinaryJoystickData(data);
        } else {
            // Інший формат - логуємо для налагодження
            Log.w(TAG, "⚠️ Unexpected data length: " + data.length);
            String textData = new String(data);
            Log.w(TAG, "⚠️ Data as text: '" + textData + "'");
        }
    }

    // Методи для перевірки стану підключень
    public boolean isPlayer1Connected() {
        return player1Gatt != null && player1Characteristic != null;
    }

    public boolean isPlayer2Connected() {
        // Для одного пристрою з двома джойстиками - якщо Player 1 підключений, то і Player 2 теж
        return player2Gatt != null && player2Characteristic != null;
    }

    // Метод для перевірки чи це один пристрій з двома джойстиками
    public boolean isSingleDeviceWithTwoJoysticks() {
        return player1Gatt != null && player2Gatt != null &&
                player1Gatt == player2Gatt && player1Characteristic == player2Characteristic;
    }

    // Метод для очищення підключень
    public void clearPlayer1Connection() {
        player1Gatt = null;
        player1Characteristic = null;
        Log.d(TAG, "Player 1 connection cleared");
    }

    public void clearPlayer2Connection() {
        player2Gatt = null;
        player2Characteristic = null;
        Log.d(TAG, "Player 2 connection cleared");
    }

    public void clearAllConnections() {
        clearPlayer1Connection();
        clearPlayer2Connection();
        joystickListener = null;
        Log.d(TAG, "All connections cleared");
    }

    // Метод для відправки повідомлень про стан
    public void notifyConnectionStatus(String status) {
        if (joystickListener != null) {
            joystickListener.onConnectionStatusChanged(status);
        }
    }

    // Методи для логування стану сервісу
    public void logStatus() {
        Log.d(TAG, "=== BLE Service Status ===");
        Log.d(TAG, "Player 1 connected: " + isPlayer1Connected());
        Log.d(TAG, "Player 2 connected: " + isPlayer2Connected());
        Log.d(TAG, "Listener set: " + (joystickListener != null));
        Log.d(TAG, "========================");
    }
}