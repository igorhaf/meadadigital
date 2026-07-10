import { MaterialCommunityIcons } from "@expo/vector-icons";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";

import AppHeader from "../components/AppHeader";
import Calendario from "../components/Calendario";
import SectionTitle from "../components/SectionTitle";
import {
  ApiError,
  buscarHorarios,
  criarAgendamento,
  type HorarioDisponibilidade,
} from "../lib/api";
import type { RootStackParamList } from "../lib/navegacao";
import { cores, formatarData, hojeISO, SERVICOS } from "../lib/theme";

type Props = NativeStackScreenProps<RootStackParamList, "Agendar">;

export default function AgendarScreen({ navigation }: Props) {
  const [servico, setServico] = useState<(typeof SERVICOS)[number]["id"]>("saude");
  const [data, setData] = useState(hojeISO());
  const [horarios, setHorarios] = useState<HorarioDisponibilidade[]>([]);
  const [horario, setHorario] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [agendando, setAgendando] = useState(false);

  const carregarHorarios = useCallback(() => {
    setCarregando(true);
    setHorario(null);
    buscarHorarios(servico, data)
      .then(setHorarios)
      .catch(() => setHorarios([]))
      .finally(() => setCarregando(false));
  }, [servico, data]);

  useEffect(() => {
    carregarHorarios();
  }, [carregarHorarios]);

  async function confirmar() {
    if (!horario) {
      return;
    }

    setAgendando(true);
    try {
      await criarAgendamento(servico, data, horario);
      const nomeServico = SERVICOS.find((s) => s.id === servico)?.label;
      Alert.alert(
        "Agendamento confirmado!",
        `${nomeServico} em ${formatarData(data)} às ${horario}.`,
      );
      carregarHorarios();
    } catch (e) {
      Alert.alert(
        "Não foi possível agendar",
        e instanceof ApiError ? e.message : "Verifique sua conexão e tente novamente.",
      );
      carregarHorarios();
    } finally {
      setAgendando(false);
    }
  }

  return (
    <View style={styles.tela}>
      <AppHeader
        titulo="Agendar Serviços"
        onVoltar={() => navigation.goBack()}
      />

      <ScrollView contentContainerStyle={styles.conteudo}>
        <SectionTitle>Agendar Serviço</SectionTitle>

        <View style={styles.grade}>
          {SERVICOS.map((item) => {
            const ativo = servico === item.id;
            return (
              <Pressable
                key={item.id}
                accessibilityRole="button"
                onPress={() => setServico(item.id)}
                style={({ pressed }) => [
                  styles.servicoCard,
                  { borderTopColor: item.cor },
                  ativo && styles.servicoAtivo,
                  pressed && styles.pressionado,
                ]}
              >
                <MaterialCommunityIcons
                  name={item.icone as never}
                  size={30}
                  color={item.cor}
                />
                <Text style={styles.servicoLabel}>{item.label}</Text>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.secao}>
          <SectionTitle sublinhado={false}>Selecione a Data</SectionTitle>
        </View>

        <Calendario
          selecionada={data}
          onSelecionar={setData}
          minima={hojeISO()}
        />

        <View style={styles.secao}>
          <SectionTitle sublinhado={false}>Horários Disponíveis</SectionTitle>
        </View>

        {carregando ? (
          <ActivityIndicator color={cores.primaria} style={styles.carregando} />
        ) : horarios.length === 0 ? (
          <Text style={styles.vazio}>
            Não foi possível carregar os horários.
          </Text>
        ) : (
          <View style={styles.slots}>
            {horarios.map((item) => {
              const ativo = horario === item.horario;
              return (
                <Pressable
                  key={item.horario}
                  accessibilityRole="button"
                  disabled={!item.disponivel}
                  onPress={() => setHorario(item.horario)}
                  style={[
                    styles.slot,
                    !item.disponivel && styles.slotOcupado,
                    ativo && styles.slotAtivo,
                  ]}
                >
                  <Text
                    style={[
                      styles.slotTexto,
                      !item.disponivel && styles.slotTextoOcupado,
                      ativo && styles.slotTextoAtivo,
                    ]}
                  >
                    {item.horario}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        )}

        <Pressable
          accessibilityRole="button"
          disabled={!horario || agendando}
          onPress={confirmar}
          style={({ pressed }) => [
            styles.confirmar,
            (!horario || agendando) && styles.confirmarDesabilitado,
            pressed && styles.pressionado,
          ]}
        >
          <Text style={styles.confirmarTexto}>
            {agendando ? "Agendando..." : "Confirmar Agendamento"}
          </Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  tela: {
    flex: 1,
    backgroundColor: cores.fundo,
  },
  conteudo: {
    padding: 16,
    paddingBottom: 40,
  },
  grade: {
    marginTop: 14,
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  servicoCard: {
    width: "47.8%",
    minHeight: 100,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: cores.borda,
    borderTopWidth: 4,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    paddingVertical: 14,
  },
  servicoAtivo: {
    borderColor: cores.primaria,
    borderWidth: 1.5,
    borderTopWidth: 4,
  },
  servicoLabel: {
    fontSize: 13.5,
    fontWeight: "600",
    color: cores.texto,
  },
  secao: {
    marginTop: 24,
    marginBottom: 12,
  },
  carregando: {
    marginTop: 8,
  },
  vazio: {
    fontSize: 13.5,
    color: cores.textoSuave,
  },
  slots: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  slot: {
    minWidth: 72,
    alignItems: "center",
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: cores.primaria,
    backgroundColor: "#ffffff",
    paddingVertical: 9,
    paddingHorizontal: 12,
  },
  slotAtivo: {
    backgroundColor: cores.primaria,
  },
  slotOcupado: {
    borderColor: cores.borda,
    backgroundColor: "#F3F4F6",
  },
  slotTexto: {
    fontSize: 13.5,
    fontWeight: "700",
    color: cores.primaria,
  },
  slotTextoAtivo: {
    color: "#ffffff",
  },
  slotTextoOcupado: {
    color: "#9CA3AF",
  },
  confirmar: {
    marginTop: 28,
    height: 48,
    borderRadius: 8,
    backgroundColor: cores.primaria,
    alignItems: "center",
    justifyContent: "center",
  },
  confirmarDesabilitado: {
    opacity: 0.5,
  },
  confirmarTexto: {
    fontSize: 15,
    fontWeight: "700",
    color: "#ffffff",
  },
  pressionado: {
    opacity: 0.85,
  },
});
