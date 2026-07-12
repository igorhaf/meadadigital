<?php

use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\CalendarController;
use App\Http\Controllers\Api\DietController;
use App\Http\Controllers\Api\ExpenseController;
use App\Http\Controllers\Api\MedicationController;
use App\Http\Controllers\Api\PageController;
use App\Http\Controllers\Api\TaskController;
use App\Http\Controllers\Api\TelegramController;
use App\Http\Controllers\Api\VaultController;
use Illuminate\Support\Facades\Route;

Route::post('/auth/login', [AuthController::class, 'login']);

Route::middleware('auth:sanctum')->group(function () {
    Route::post('/auth/logout', [AuthController::class, 'logout']);
    Route::get('/auth/me', [AuthController::class, 'me']);

    Route::get('/tree', [PageController::class, 'tree']);
    Route::get('/users', fn () => \App\Models\User::select('id', 'name')->orderBy('name')->get());

    Route::post('/pages', [PageController::class, 'store']);
    Route::get('/pages/{page}', [PageController::class, 'show']);
    Route::put('/pages/{page}', [PageController::class, 'update']);
    Route::patch('/pages/{page}/move', [PageController::class, 'move']);
    Route::delete('/pages/{page}', [PageController::class, 'destroy']);

    // Vault (senhas) — cifrado; reveal sob demanda
    Route::get('/pages/{page}/vault', [VaultController::class, 'index']);
    Route::post('/pages/{page}/vault', [VaultController::class, 'store']);
    Route::put('/pages/{page}/vault/{entry}', [VaultController::class, 'update']);
    Route::get('/pages/{page}/vault/{entry}/reveal', [VaultController::class, 'reveal']);
    Route::delete('/pages/{page}/vault/{entry}', [VaultController::class, 'destroy']);

    // Calendário (sync Google best-effort)
    Route::get('/pages/{page}/events', [CalendarController::class, 'index']);
    Route::post('/pages/{page}/events', [CalendarController::class, 'store']);
    Route::put('/pages/{page}/events/{event}', [CalendarController::class, 'update']);
    Route::delete('/pages/{page}/events/{event}', [CalendarController::class, 'destroy']);

    // Tarefas
    Route::get('/pages/{page}/tasks', [TaskController::class, 'index']);
    Route::post('/pages/{page}/tasks', [TaskController::class, 'store']);
    Route::put('/pages/{page}/tasks/{task}', [TaskController::class, 'update']);
    Route::delete('/pages/{page}/tasks/{task}', [TaskController::class, 'destroy']);

    // Remédios
    Route::get('/pages/{page}/medications', [MedicationController::class, 'index']);
    Route::post('/pages/{page}/medications', [MedicationController::class, 'store']);
    Route::put('/pages/{page}/medications/{medication}', [MedicationController::class, 'update']);
    Route::post('/pages/{page}/medications/{medication}/log', [MedicationController::class, 'log']);
    Route::delete('/pages/{page}/medications/{medication}', [MedicationController::class, 'destroy']);

    // Dieta (perfil + geração assíncrona via Elo)
    Route::put('/pages/{page}/diet/profile', [DietController::class, 'updateProfile']);
    Route::post('/pages/{page}/diet/generate', [DietController::class, 'generate']);

    // Gastos (sync Google Sheets best-effort)
    Route::get('/pages/{page}/expenses', [ExpenseController::class, 'index']);
    Route::post('/pages/{page}/expenses', [ExpenseController::class, 'store']);
    Route::put('/pages/{page}/expenses/{expense}', [ExpenseController::class, 'update']);
    Route::delete('/pages/{page}/expenses/{expense}', [ExpenseController::class, 'destroy']);

    // Telegram — código de vínculo
    Route::post('/telegram/link-code', [TelegramController::class, 'linkCode']);
});
