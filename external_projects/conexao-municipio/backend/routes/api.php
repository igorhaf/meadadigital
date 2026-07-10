<?php

use App\Http\Controllers\Api\AgendamentoController;
use App\Http\Controllers\Api\ConfiguracaoController;
use App\Http\Controllers\Api\DashboardController;
use App\Http\Controllers\Api\DenunciaController;
use App\Http\Controllers\Api\NoticiaController;
use App\Http\Controllers\Api\UserController;
use Illuminate\Support\Facades\Route;

Route::pattern('denuncia', '[0-9]{1,18}');
Route::pattern('usuario', '[0-9]{1,18}');
Route::pattern('agendamento', '[0-9]{1,18}');

Route::get('/dashboard', DashboardController::class);

Route::get('/denuncias/export', [DenunciaController::class, 'export']);
Route::apiResource('denuncias', DenunciaController::class);

Route::apiResource('usuarios', UserController::class)->parameters(['usuarios' => 'usuario']);

Route::get('/configuracoes', [ConfiguracaoController::class, 'index']);
Route::put('/configuracoes', [ConfiguracaoController::class, 'update']);

Route::get('/noticias', [NoticiaController::class, 'index']);

Route::get('/agendamentos/horarios', [AgendamentoController::class, 'horarios']);
Route::apiResource('agendamentos', AgendamentoController::class)
    ->only(['index', 'store', 'destroy']);
