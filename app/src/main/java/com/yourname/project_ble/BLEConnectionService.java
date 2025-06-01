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

    public void parsePlayer1Data(String data) {
        try {
            Log.d(TAG, "🎮 RAW P1 data: '" + data + "'");

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
            Log.d(TAG, "🎮 RAW P2 data: '" + data + "'");

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

    // Метод для парсингу даних з двох джойстиків в одному повідомленні
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
        return player2Gatt != null && player2Characteristic != null;
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