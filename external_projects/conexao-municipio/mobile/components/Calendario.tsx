import { MaterialCommunityIcons } from "@expo/vector-icons";
import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { cores } from "../lib/theme";

const MESES = [
  "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
  "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
];

const DIAS_SEMANA = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];

interface CalendarioProps {
  /** Data selecionada em formato ISO (YYYY-MM-DD). */
  selecionada: string;
  onSelecionar: (dataISO: string) => void;
  /** Desabilita dias anteriores a esta data ISO (opcional). */
  minima?: string;
}

function paraISO(ano: number, mes: number, dia: number): string {
  return `${ano}-${String(mes + 1).padStart(2, "0")}-${String(dia).padStart(2, "0")}`;
}

export default function Calendario({
  selecionada,
  onSelecionar,
  minima,
}: CalendarioProps) {
  const [ano, setAno] = useState(() => Number(selecionada.slice(0, 4)));
  const [mes, setMes] = useState(() => Number(selecionada.slice(5, 7)) - 1);

  const primeiroDiaSemana = new Date(ano, mes, 1).getDay();
  const totalDias = new Date(ano, mes + 1, 0).getDate();

  const celulas: (number | null)[] = [
    ...Array.from({ length: primeiroDiaSemana }, () => null),
    ...Array.from({ length: totalDias }, (_, i) => i + 1),
  ];
  while (celulas.length % 7 !== 0) {
    celulas.push(null);
  }

  const semanas: (number | null)[][] = [];
  for (let i = 0; i < celulas.length; i += 7) {
    semanas.push(celulas.slice(i, i + 7));
  }

  function mudarMes(delta: number) {
    const novo = new Date(ano, mes + delta, 1);
    setAno(novo.getFullYear());
    setMes(novo.getMonth());
  }

  return (
    <View style={styles.container}>
      <View style={styles.cabecalho}>
        <Pressable
          onPress={() => mudarMes(-1)}
          accessibilityRole="button"
          accessibilityLabel="Mês anterior"
          style={styles.seta}
        >
          <MaterialCommunityIcons name="chevron-left" size={20} color="#fff" />
        </Pressable>
        <Text style={styles.mes}>
          {MESES[mes]} {ano}
        </Text>
        <Pressable
          onPress={() => mudarMes(1)}
          accessibilityRole="button"
          accessibilityLabel="Próximo mês"
          style={styles.seta}
        >
          <MaterialCommunityIcons name="chevron-right" size={20} color="#fff" />
        </Pressable>
      </View>

      <View style={styles.corpo}>
        <View style={styles.linha}>
          {DIAS_SEMANA.map((dia) => (
            <Text key={dia} style={styles.diaSemana}>
              {dia}
            </Text>
          ))}
        </View>

        {semanas.map((semana, i) => (
          <View key={i} style={styles.linha}>
            {semana.map((dia, j) => {
              if (dia === null) {
                return <View key={j} style={styles.celula} />;
              }

              const iso = paraISO(ano, mes, dia);
              const ativa = iso === selecionada;
              const desabilitada = minima !== undefined && iso < minima;

              return (
                <Pressable
                  key={j}
                  style={styles.celula}
                  disabled={desabilitada}
                  onPress={() => onSelecionar(iso)}
                  accessibilityRole="button"
                  accessibilityLabel={`Dia ${dia}`}
                >
                  <View style={[styles.dia, ativa && styles.diaAtivo]}>
                    <Text
                      style={[
                        styles.diaTexto,
                        desabilitada && styles.diaDesabilitado,
                      ]}
                    >
                      {dia}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: 10,
    overflow: "hidden",
  },
  cabecalho: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: cores.primaria,
    paddingHorizontal: 10,
    paddingVertical: 10,
  },
  seta: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: cores.primariaEscura,
    alignItems: "center",
    justifyContent: "center",
  },
  mes: {
    fontSize: 15,
    fontWeight: "800",
    color: "#ffffff",
  },
  corpo: {
    backgroundColor: cores.calendarioFundo,
    paddingHorizontal: 6,
    paddingVertical: 8,
  },
  linha: {
    flexDirection: "row",
  },
  diaSemana: {
    flex: 1,
    textAlign: "center",
    fontSize: 12,
    fontWeight: "600",
    color: "#9E9E9E",
    paddingVertical: 6,
  },
  celula: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 4,
  },
  dia: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: "center",
    justifyContent: "center",
  },
  diaAtivo: {
    backgroundColor: cores.primaria,
  },
  diaTexto: {
    fontSize: 12.5,
    fontWeight: "700",
    color: "#ffffff",
  },
  diaDesabilitado: {
    color: "#555555",
  },
});
