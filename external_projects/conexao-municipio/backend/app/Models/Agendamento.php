<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Agendamento extends Model
{
    public const SERVICO_SAUDE = 'saude';
    public const SERVICO_DOCUMENTOS = 'documentos';
    public const SERVICO_CRAS = 'cras';
    public const SERVICO_LICENCAS = 'licencas';

    public const SERVICOS = [
        self::SERVICO_SAUDE,
        self::SERVICO_DOCUMENTOS,
        self::SERVICO_CRAS,
        self::SERVICO_LICENCAS,
    ];

    /** Grade fixa de horários de atendimento da prefeitura. */
    public const HORARIOS = [
        '08:00', '09:00', '10:00', '11:00',
        '13:00', '14:00', '15:00', '16:00', '17:00',
    ];

    protected $table = 'agendamentos';

    protected $fillable = [
        'servico',
        'data',
        'horario',
    ];

    protected function casts(): array
    {
        return [
            'data' => 'date:Y-m-d',
        ];
    }
}
