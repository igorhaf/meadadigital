<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\ContactMessage;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;

class MessageController extends Controller
{
    public function index(): View
    {
        $messages = ContactMessage::latest()->paginate(15);
        $unread = ContactMessage::where('is_read', false)->count();

        return view('admin.messages.index', compact('messages', 'unread'));
    }

    public function toggleRead(ContactMessage $message): RedirectResponse
    {
        $message->update(['is_read' => ! $message->is_read]);

        return back();
    }

    public function destroy(ContactMessage $message): RedirectResponse
    {
        $message->delete();

        return back()->with('status', 'Mensagem removida.');
    }
}
