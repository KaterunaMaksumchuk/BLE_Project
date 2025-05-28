package com.yourname.project_ble;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    private Button playButton;
    private Button settingsButton;
    private Button exitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        initializeUI();
        setupClickListeners();
    }

    private void initializeUI() {
        playButton = findViewById(R.id.playButton);
        settingsButton = findViewById(R.id.settingsButton);
        exitButton = findViewById(R.id.exitButton);
    }

    private void setupClickListeners() {
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Переходимо до SnakeGameActivity (гра змійка)
                Intent intent = new Intent(MenuActivity.this, SnakeGameActivity.class);
                startActivity(intent);
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Тут можна додати перехід до налаштувань
                // Поки що просто показуємо повідомлення
                android.widget.Toast.makeText(MenuActivity.this,
                        "Налаштування ще не реалізовані",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Закриваємо додаток
                finishAffinity();
            }
        });
    }
}