@extends('layouts.dashboard')

@section('title', 'Mensagens')

@section('content')
    <div class="mb-6 flex items-center gap-3">
        <h1 class="text-2xl font-extrabold text-neutral-900">Mensagens de contato</h1>
        @if($unread)<span class="chip bg-gold-600 text-white">{{ $unread }} não lida(s)</span>@endif
    </div>

    <div class="space-y-3">
        @forelse($messages as $message)
            <div class="card p-5 {{ $message->is_read ? '' : 'border-brand-300 bg-brand-50/40' }}">
                <div class="flex flex-wrap items-start justify-between gap-2">
                    <div>
                        <p class="font-semibold text-neutral-900">{{ $message->subject ?: '(sem assunto)' }}</p>
                        <p class="text-sm text-neutral-500">{{ $message->name }} · <a href="mailto:{{ $message->email }}" class="text-brand-700 hover:underline">{{ $message->email }}</a> · {{ $message->created_at->format('d/m/Y H:i') }}</p>
                    </div>
                    <div class="flex gap-1">
                        <form method="POST" action="{{ route('admin.messages.toggle', $message) }}">
                            @csrf
                            <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-neutral-500 hover:bg-neutral-100">{{ $message->is_read ? 'Marcar não lida' : 'Marcar lida' }}</button>
                        </form>
                        <form method="POST" action="{{ route('admin.messages.destroy', $message) }}" onsubmit="return confirm('Remover mensagem?')">
                            @csrf @method('DELETE')
                            <button class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-500 hover:bg-red-50">Excluir</button>
                        </form>
                    </div>
                </div>
                <p class="mt-3 whitespace-pre-line text-sm text-neutral-700">{{ $message->message }}</p>
            </div>
        @empty
            <div class="card flex flex-col items-center justify-center py-16 text-center">
                <div class="text-5xl">📭</div>
                <p class="mt-4 font-semibold text-neutral-700">Nenhuma mensagem ainda</p>
            </div>
        @endforelse
    </div>

    <div class="mt-6">{{ $messages->onEachSide(1)->links() }}</div>
@endsection
