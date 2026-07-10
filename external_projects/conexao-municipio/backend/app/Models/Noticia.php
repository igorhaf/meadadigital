<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Noticia extends Model
{
    protected $table = 'noticias';

    protected $fillable = [
        'titulo',
        'resumo',
        'data',
    ];

    protected function casts(): array
    {
        return [
            'data' => 'date:Y-m-d',
        ];
    }
}
