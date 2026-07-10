@extends('layouts.app')

@section('title', $term !== '' ? "Busca: {$term}" : 'Buscar produtos')

@section('content')
    @include('partials.listing')
@endsection
