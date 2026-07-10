<?php

namespace App\Services\Shipping\Contracts;

interface Carrier
{
    public function name(): string;

    public function enabled(): bool;

    /**
     * @param  array{weight:int,length:int,width:int,height:int}  $package
     * @return array<int,array{id:string,carrier:string,service:string,price:float,days:int,label:string}>
     */
    public function quote(string $destCep, array $package, float $subtotal): array;
}
