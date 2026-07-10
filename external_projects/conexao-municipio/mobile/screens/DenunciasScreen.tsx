import { MaterialCommunityIcons } from "@expo/vector-icons";
import { useFocusEffect } from "@react-navigation/native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useCallback, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";

import AppHeader from "../components/AppHeader";
import SectionTitle from "../components/SectionTitle";
import StatusPill from "../components/StatusPill";
import { buscarMinhasDenuncias, type Denuncia } from "../lib/api";
import type { RootStackParamList } from "../lib/navegacao";
import {
  CATEGORIAS,
  categoriaInfo,
  cores,
  formatarData,
} from "../lib/theme";

type Props = NativeStackScreenProps<RootStackParamList, "Denuncias">;

export default function DenunciasScreen({ navigation }: Props) {
  const [denuncias, setDenuncias] = useState<Denuncia[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      let ativo = true;

      buscarMinhasDenuncias()
        .then((lista) => {
          if (ativo) {
            setDenuncias(lista);
            setErro(null);
          }
        })
        .catch(() => {
          if (ativo) {
            setErro("Não foi possível carregar suas denúncias.");
          }
        })
        .finally(() => {
          if (ativo) {
            setCarregando(false);
          }
        });

      return () => {
        ativo = false;
      };
    }, []),
  );

  return (
    <View style={styles.tela}>
      <AppHeader
        titulo="Denúncias Urbanas"
        onVoltar={() => navigation.goBack()}
      />

      <ScrollView contentContainerStyle={styles.conteudo}>
        <Pressable
          accessibilityRole="button"
          onPress={() => navigation.navigate("NovaDenuncia")}
          style={({ pressed }) => [
            styles.novaDenuncia,
            pressed && styles.pressionado,
          ]}
        >
          <MaterialCommunityIcons name="plus" size={18} color="#fff" />
          <Text style={styles.novaDenunciaTexto}>Nova Denúncia</Text>
        </Pressable>

        <View style={styles.secao}>
          <SectionTitle sublinhado={false}>Categorias</SectionTitle>
        </View>

        <View style={styles.grade}>
          {CATEGORIAS.map((categoria) => (
            <Pressable
              key={categoria.id}
              accessibilityRole="button"
              onPress={() =>
                navigation.navigate("NovaDenuncia", {
                  categoria: categoria.id,
                })
              }
              style={({ pressed }) => [
                styles.categoriaCard,
                pressed && styles.pressionado,
              ]}
            >
              <MaterialCommunityIcons
                name={categoria.icone as never}
                size={26}
                color={cores.primaria}
              />
              <Text style={styles.categoriaLabel}>{categoria.label}</Text>
            </Pressable>
          ))}
        </View>

        <View style={styles.secao}>
          <SectionTitle sublinhado={false}>Suas Denúncias</SectionTitle>
        </View>

        {carregando ? (
          <ActivityIndicator
            color={cores.primaria}
            style={styles.carregando}
          />
        ) : erro ? (
          <Text style={styles.erro}>{erro}</Text>
        ) : denuncias.length === 0 ? (
          <Text style={styles.vazio}>
            Você ainda não registrou nenhuma denúncia.
          </Text>
        ) : (
          denuncias.map((denuncia) => {
            const info = categoriaInfo(denuncia.categoria);

            return (
              <View key={denuncia.id} style={styles.denunciaCard}>
                <View
                  style={[styles.chip, { backgroundColor: info.chipCor }]}
                >
                  <Text style={styles.chipTexto}>{info.chip}</Text>
                </View>
                <View style={styles.denunciaCorpo}>
                  <View style={styles.denunciaTopo}>
                    <Text style={styles.denunciaTitulo} numberOfLines={1}>
                      {denuncia.titulo}
                    </Text>
                    <StatusPill status={denuncia.status} />
                  </View>
                  {denuncia.descricao ? (
                    <Text style={styles.denunciaDescricao} numberOfLines={2}>
                      {denuncia.descricao}
                    </Text>
                  ) : null}
                  <Text style={styles.denunciaData}>
                    {formatarData(denuncia.data)}
                  </Text>
                </View>
              </View>
            );
          })
        )}
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
  novaDenuncia: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    height: 46,
    borderRadius: 8,
    backgroundColor: cores.primaria,
  },
  novaDenunciaTexto: {
    fontSize: 14.5,
    fontWeight: "700",
    color: "#ffffff",
  },
  secao: {
    marginTop: 22,
    marginBottom: 12,
  },
  grade: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  categoriaCard: {
    width: "31%",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 16,
  },
  categoriaLabel: {
    fontSize: 12.5,
    fontWeight: "600",
    color: cores.texto,
  },
  carregando: {
    marginTop: 16,
  },
  erro: {
    fontSize: 13.5,
    color: cores.vermelho,
  },
  vazio: {
    fontSize: 13.5,
    color: cores.textoSuave,
  },
  denunciaCard: {
    flexDirection: "row",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: "#ffffff",
    overflow: "hidden",
    marginBottom: 12,
  },
  chip: {
    width: 64,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 4,
  },
  chipTexto: {
    fontSize: 11,
    fontWeight: "700",
    color: "#ffffff",
    textAlign: "center",
  },
  denunciaCorpo: {
    flex: 1,
    padding: 10,
  },
  denunciaTopo: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  denunciaTitulo: {
    flex: 1,
    fontSize: 14,
    fontWeight: "700",
    color: cores.texto,
  },
  denunciaDescricao: {
    marginTop: 3,
    fontSize: 12.5,
    color: cores.textoSuave,
    lineHeight: 17,
  },
  denunciaData: {
    marginTop: 6,
    fontSize: 11.5,
    color: cores.textoSuave,
  },
  pressionado: {
    opacity: 0.85,
  },
});
