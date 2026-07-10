<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Banner;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class BannerController extends Controller
{
    public function index(): View
    {
        $banners = Banner::orderBy('placement')->orderBy('position')->get()->groupBy('placement');

        return view('admin.banners.index', compact('banners'));
    }

    public function create(): View
    {
        return view('admin.banners.create', ['banner' => new Banner([
            'placement' => 'hero', 'bg_from' => '#7c3aed', 'bg_to' => '#4f46e5', 'is_active' => true,
        ])]);
    }

    public function store(Request $request): RedirectResponse
    {
        Banner::create($this->validated($request));

        return redirect()->route('admin.banners.index')->with('status', 'Banner criado.');
    }

    public function edit(Banner $banner): View
    {
        return view('admin.banners.edit', compact('banner'));
    }

    public function update(Request $request, Banner $banner): RedirectResponse
    {
        $banner->update($this->validated($request));

        return redirect()->route('admin.banners.index')->with('status', 'Banner atualizado.');
    }

    public function destroy(Banner $banner): RedirectResponse
    {
        $banner->delete();

        return redirect()->route('admin.banners.index')->with('status', 'Banner removido.');
    }

    private function validated(Request $request): array
    {
        return $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'subtitle' => ['nullable', 'string', 'max:255'],
            'cta_label' => ['nullable', 'string', 'max:50'],
            'link_url' => ['nullable', 'string', 'max:255'],
            'bg_from' => ['required', 'string', 'max:20'],
            'bg_to' => ['required', 'string', 'max:20'],
            'placement' => ['required', 'in:hero,strip'],
            'position' => ['nullable', 'integer', 'min:0'],
            'is_active' => ['nullable', 'boolean'],
        ]);
    }
}
