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
     * Новий метод для парсингу 4-байтових даних: X1, Y1, X2, Y2
     * @param data - масив з 4 байтів від nRF52832
     */
    public void parseBinaryJoystickData(byte[] data) {
        try {
            if (data == null || data.length < 4) {
                Log.w(TAG, "❌ Invalid data length: " + (data != null ? data.length : "null"));
                return;
            }

            // Конвертуємо байти в unsigned int (0-255)
            int x1 = data[0] & 0xFF;  // Джойстик 1, вісь X
            int y1 = data[1] & 0xFF;  // Джойстик 1, вісь Y
            int x2 = data[2] & 0xFF;  // Джойстик 2, вісь X
            int y2 = data[3] & 0xFF;  // Джойстик 2, вісь Y

            Log.d(TAG, "🎮 RAW BINARY: [" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + "]");

            // Конвертуємо з діапазону 0-255 в 0-1023 (для сумісності з старим кодом)
            int scaled_x1 = (x1 * 1023) / 255;
            int scaled_y1 = (y1 * 1023) / 255;
            int scaled_x2 = (x2 * 1023) / 255;
            int scaled_y2 = (y2 * 1023) / 255;

            Log.d(TAG, "✅ SCALED: P1(X=" + scaled_x1 + ", Y=" + scaled_y1 + ") P2(X=" + scaled_x2 + ", Y=" + scaled_y2 + ")");

            // Відправляємо дані слухачам
            if (joystickListener != null) {
                joystickListener.onPlayer1Data(scaled_x1, scaled_y1);
                joystickListener.onPlayer2Data(scaled_x2, scaled_y2);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Binary parse error: " + e.getMessage());
        }
    }

    /**
     * Метод для парсингу текстових даних (залишаємо для зворотної сумісності)
     */
    public void parsePlayer1Data(String data) {
        try {
            Log.d(TAG, "🎮 RAW P1 TEXT data: '" + data + "'");

            String cleanData = data.trim()
                    .replace("?", "")
                    .replace("\u0000", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .replaceAll("[^0-9:]", "");

            Log.d(TAG, "🎮 CLEAN P1 data: '" + cleanData + "'");

            String[] parts = cleanData.split(":");

            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (joystickListener != null) {
                    joystickListener.onPlayer1Data(x, y);
                }

                Log.d(TAG, "✅ Player 1 - X: " + x + ", Y: " + y);
            } else {
                Log.w(TAG, "❌ Invalid P1 format: parts=" + parts.length + ", data='" + cleanData + "'");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ P1 parse error: " + e.getMessage() + " for: '" + data + "'");
        }
    }

    public void parsePlayer2Data(String data) {
        try {
            Log.d(TAG, "🎮 RAW P2 TEXT data: '" + data + "'");

            String cleanData = data.trim()
                    .replace("?", "")
                    .replace("\u0000", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .replaceAll("[^0-9:]", "");

            Log.d(TAG, "🎮 CLEAN P2 data: '" + cleanData + "'");

            String[] parts = cleanData.split(":");

            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (joystickListener != null) {
                    joystickListener.onPlayer2Data(x, y);
                }

                Log.d(TAG, "✅ Player 2 - X: " + x + ", Y: " + y);
            } else {
                Log.w(TAG, "❌ Invalid P2 format: parts=" + parts.length + ", data='" + cleanData + "'");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ P2 parse error: " + e.getMessage() + " for: '" + data + "'");
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
            // Старий текстовий формат
            Log.d(TAG, "🎯 Using TEXT format");
            String textData = new String(data);
            parseCombinedJoystickData(textData, 1);
        }
    }

    /**
     * Метод для парсингу текстових даних з двох джойстиків (залишаємо для сумісності)
     */
    public void parseCombinedJoystickData(String data, int playerNumber) {
        try {
            Log.d(TAG, "🎮 RAW COMBINED data from P" + playerNumber + ": '" + data + "'");

            String cleanData = data.trim()
                    .replace("?", "")
                    .replace("\u0000", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .replaceAll("[^0-9:,]", "");

            Log.d(TAG, "🎮 CLEAN COMBINED data: '" + cleanData + "'");

            // Спробуємо різні формати
            if (cleanData.contains(",")) {
                // Формат: X1:Y1,X2:Y2
                String[] joysticks = cleanData.split(",");
                if (joysticks.length == 2) {
                    parseJoystickPair(joysticks[0], 1);
                    parseJoystickPair(joysticks[1], 2);
                    return;
                }
            }

            // Формат: X1:Y1:X2:Y2
            String[] parts = cleanData.split(":");
            if (parts.length == 4) {
                int x1 = Integer.parseInt(parts[0]);
                int y1 = Integer.parseInt(parts[1]);
                int x2 = Integer.parseInt(parts[2]);
                int y2 = Integer.parseInt(parts[3]);

                if (joystickListener != null) {
                    joystickListener.onPlayer1Data(x1, y1);
                    joystickListener.onPlayer2Data(x2, y2);
                }

                Log.d(TAG, "✅ P1: X=" + x1 + " Y=" + y1 + ", P2: X=" + x2 + " Y=" + y2);
                return;
            }

            // ТИМЧАСОВЕ РІШЕННЯ: якщо приходить тільки один джойстик (X:Y), симулюємо другий
            if (parts.length == 2) {
                int x1 = Integer.parseInt(parts[0]);
                int y1 = Integer.parseInt(parts[1]);

                // Симулюємо другий джойстик зі зміщенням +50
                int x2 = Math.min(1023, x1 + 50);
                int y2 = Math.min(1023, y1 + 50);

                if (joystickListener != null) {
                    joystickListener.onPlayer1Data(x1, y1);
                    joystickListener.onPlayer2Data(x2, y2); // СИМУЛЯЦІЯ!
                }

                Log.d(TAG, "🎯 СИМУЛЯЦІЯ: P1: X=" + x1 + " Y=" + y1 + ", P2: X=" + x2 + " Y=" + y2 + " (симульований)");
                return;
            }

            // Якщо не підходить жоден формат - парсимо як один джойстик
            if (playerNumber == 1) {
                parsePlayer1Data(data);
            } else {
                parsePlayer2Data(data);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Combined parse error: " + e.getMessage() + " for: '" + data + "'");
        }
    }

    private void parseJoystickPair(String joystickData, int playerNumber) {
        try {
            String[] parts = joystickData.split(":");
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);

                if (joystickListener != null) {
                    if (playerNumber == 1) {
                        joystickListener.onPlayer1Data(x, y);
                    } else {
                        joystickListener.onPlayer2Data(x, y);
                    }
                }

                Log.d(TAG, "✅ Player " + playerNumber + " - X: " + x + ", Y: " + y);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Parse pair error: " + e.getMessage());
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