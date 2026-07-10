<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Carbon;

class Denuncia extends Model
{
    public const STATUS_PENDENTE = 'pendente';
    public const STATUS_EM_ANDAMENTO = 'em_andamento';
    public const STATUS_RESOLVIDO = 'resolvido';
    public const STATUS_ARQUIVADO = 'arquivado';

    public const STATUSES = [
        self::STATUS_PENDENTE,
        self::STATUS_EM_ANDAMENTO,
        self::STATUS_RESOLVIDO,
        self::STATUS_ARQUIVADO,
    ];

    public const PERIODOS = ['semana', 'mes', 'todos'];

    public const CATEGORIA_BURACOS = 'buracos';
    public const CATEGORIA_ILUMINACAO = 'iluminacao';
    public const CATEGORIA_LIXO = 'lixo';
    public const CATEGORIA_ESGOTO = 'esgoto';
    public const CATEGORIA_TRANSITO = 'transito';
    public const CATEGORIA_VANDALISMO = 'vandalismo';
    public const CATEGORIA_OUTROS = 'outros';

    public const CATEGORIAS = [
        self::CATEGORIA_BURACOS,
        self::CATEGORIA_ILUMINACAO,
        self::CATEGORIA_LIXO,
        self::CATEGORIA_ESGOTO,
        self::CATEGORIA_TRANSITO,
        self::CATEGORIA_VANDALISMO,
        self::CATEGORIA_OUTROS,
    ];

    public const ORIGEM_WEB = 'web';
    public const ORIGEM_MOBILE = 'mobile';

    public const ORIGENS = [
        self::ORIGEM_WEB,
        self::ORIGEM_MOBILE,
    ];

    public function scopeNoPeriodo(Builder $query, ?string $periodo): Builder
    {
        return match ($periodo) {
            'semana' => $query->where('data', '>=', Carbon::now()->startOfWeek()->toDateString()),
            'mes' => $query->where('data', '>=', Carbon::now()->startOfMonth()->toDateString()),
            default => $query,
        };
    }

    protected $table = 'denuncias';

    protected $fillable = [
        'titulo',
        'descricao',
        'endereco',
        'categoria',
        'origem',
        'status',
        'data',
    ];

    protected function casts(): array
    {
        return [
            'data' => 'date:Y-m-d',
        ];
    }
}
