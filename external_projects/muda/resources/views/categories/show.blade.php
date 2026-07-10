@extends('layouts.app')

@section('title', $category->name)
@section('description', "Encontre {$category->name} de brechó no marketplace Muda: peças únicas, seminovas e usadas com preço justo.")

@section('content')
    @include('partials.listing')
@endsection
