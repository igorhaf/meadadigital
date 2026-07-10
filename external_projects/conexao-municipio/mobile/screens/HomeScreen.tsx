import { MaterialCommunityIcons } from "@expo/vector-icons";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect, useRef, useState } from "react";
import {
  Alert,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import SectionTitle from "../components/SectionTitle";
import { buscarNoticias, type Noticia } from "../lib/api";
import { cores, formatarData } from "../lib/theme";
import type { RootStackParamList } from "../lib/navegacao";

type Props = NativeStackScreenProps<RootStackParamList, "Home"> & {
  onLogout: () => void;
};

export default function HomeScreen({ navigation, onLogout }: Props) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [noticias, setNoticias] = useState<Noticia[]>([]);
  const [pagina, setPagina] = useState(0);
  const larguraCard = width - 32;
  const listaRef = useRef<FlatList<Noticia>>(null);

  useEffect(() => {
    buscarNoticias()
      .then(setNoticias)
      .catch(() => {
        // sem notícias: a seção simplesmente fica vazia
      });
  }, []);

  function abrirConfiguracoes() {
    Alert.alert("Configurações", undefined, [
      { text: "Sair da conta", style: "destructive", onPress: onLogout },
      { text: "Cancelar", style: "cancel" },
    ]);
  }

  const acoes = [
    {
      label: "Fazer uma\nDenúncia",
      icone: "alert-circle",
      cor: cores.primaria,
      onPress: () => navigation.navigate("Denuncias"),
    },
    {
      label: "Agendar Serviço",
      icone: "calendar-check",
      cor: cores.azulEscuro,
      onPress: () => navigation.navigate("Agendar"),
    },
    {
      label: "Ver Mapa da\nCidade",
      icone: "map-legend",
      cor: cores.amarelo,
      onPress: () =>
        Alert.alert("Em breve", "O mapa da cidade estará disponível em breve."),
    },
    {
      label: "Falar com a\nPrefeitura",
      icone: "forum",
      cor: cores.amarelo,
      onPress: () =>
        Alert.alert("Em breve", "O canal de atendimento estará disponível em breve."),
    },
  ] as const;

  return (
    <View style={styles.tela}>
      <View style={[styles.header, { paddingTop: insets.top + 10 }]}>
        <View style={styles.avatar}>
          <Text style={styles.avatarLetra}>U</Text>
        </View>
        <Text style={styles.saudacao}>Olá, Cidadão</Text>
        <View style={styles.headerAcoes}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Notificações"
            onPress={() =>
              Alert.alert("Notificações", "Você tem 2 notificações não lidas.")
            }
            style={styles.sino}
          >
            <MaterialCommunityIcons name="bell" size={22} color="#fff" />
            <View style={styles.badge}>
              <Text style={styles.badgeTexto}>2</Text>
            </View>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Configurações"
            onPress={abrirConfiguracoes}
          >
            <MaterialCommunityIcons name="cog" size={22} color="#fff" />
          </Pressable>
        </View>
      </View>

      <ScrollView contentContainerStyle={styles.conteudo}>
        <SectionTitle>Notícias da Cidade</SectionTitle>

        {noticias.length > 0 ? (
          <View style={styles.carrossel}>
            <FlatList
              ref={listaRef}
              data={noticias}
              horizontal
              pagingEnabled
              showsHorizontalScrollIndicator={false}
              keyExtractor={(n) => String(n.id)}
              snapToInterval={larguraCard}
              decelerationRate="fast"
              onMomentumScrollEnd={(e) =>
                setPagina(
                  Math.round(e.nativeEvent.contentOffset.x / larguraCard),
                )
              }
              renderItem={({ item, index }) => (
                <View style={[styles.noticiaCard, { width: larguraCard }]}>
                  <View style={styles.noticiaBanner}>
                    <Text style={styles.noticiaBannerTexto}>
                      Notícia {index + 1}
                    </Text>
                  </View>
                  <View style={styles.noticiaCorpo}>
                    <Text style={styles.noticiaTitulo}>{item.titulo}</Text>
                    <Text style={styles.noticiaResumo}>{item.resumo}</Text>
                    <View style={styles.noticiaRodape}>
                      <View style={styles.dots}>
                        {noticias.map((_, i) => (
                          <View
                            key={i}
                            style={[
                              styles.dot,
                              i === pagina && styles.dotAtivo,
                            ]}
                          />
                        ))}
                      </View>
                      <Text style={styles.noticiaData}>
                        {formatarData(item.data)}
                      </Text>
                    </View>
                  </View>
                </View>
              )}
            />
          </View>
        ) : null}

        <View style={styles.secao}>
          <SectionTitle>O que você precisa?</SectionTitle>
        </View>

        <View style={styles.grade}>
          {acoes.map((acao) => (
            <Pressable
              key={acao.label}
              onPress={acao.onPress}
              accessibilityRole="button"
              style={({ pressed }) => [
                styles.acaoCard,
                { borderTopColor: acao.cor },
                pressed && styles.pressionado,
              ]}
            >
              <MaterialCommunityIcons
                name={acao.icone}
                size={30}
                color={acao.cor}
              />
              <Text style={styles.acaoLabel}>{acao.label}</Text>
            </Pressable>
          ))}

          <Pressable
            onPress={() =>
              Alert.alert(
                "Emergência",
                "Polícia: 190\nSAMU: 192\nBombeiros: 193",
              )
            }
            accessibilityRole="button"
            style={({ pressed }) => [
              styles.acaoCard,
              styles.emergenciaCard,
              pressed && styles.pressionado,
            ]}
          >
            <MaterialCommunityIcons
              name="alert-circle"
              size={30}
              color={cores.vermelho}
            />
            <Text style={[styles.acaoLabel, styles.emergenciaLabel]}>
              Emergência
            </Text>
          </Pressable>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  tela: {
    flex: 1,
    backgroundColor: cores.fundo,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    backgroundColor: cores.primaria,
    paddingHorizontal: 14,
    paddingBottom: 12,
  },
  avatar: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarLetra: {
    fontSize: 15,
    fontWeight: "800",
    color: cores.azulEscuro,
  },
  saudacao: {
    flex: 1,
    fontSize: 16,
    fontWeight: "700",
    color: "#ffffff",
  },
  headerAcoes: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
  },
  sino: {
    position: "relative",
  },
  badge: {
    position: "absolute",
    top: -5,
    right: -7,
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: cores.vermelho,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 3,
  },
  badgeTexto: {
    fontSize: 9.5,
    fontWeight: "800",
    color: "#ffffff",
  },
  conteudo: {
    padding: 16,
    paddingBottom: 40,
  },
  carrossel: {
    marginTop: 12,
  },
  noticiaCard: {
    borderRadius: 10,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: "#ffffff",
    overflow: "hidden",
  },
  noticiaBanner: {
    height: 150,
    backgroundColor: cores.azul,
    alignItems: "center",
    justifyContent: "center",
  },
  noticiaBannerTexto: {
    fontSize: 36,
    fontWeight: "800",
    color: "#ffffff",
  },
  noticiaCorpo: {
    padding: 12,
  },
  noticiaTitulo: {
    fontSize: 14.5,
    fontWeight: "700",
    color: cores.texto,
  },
  noticiaResumo: {
    marginTop: 4,
    fontSize: 13,
    color: cores.textoSuave,
    lineHeight: 18,
  },
  noticiaRodape: {
    marginTop: 10,
    flexDirection: "row",
    alignItems: "center",
  },
  dots: {
    flex: 1,
    flexDirection: "row",
    justifyContent: "center",
    gap: 5,
    marginLeft: 54,
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: "#E0E0E0",
  },
  dotAtivo: {
    backgroundColor: cores.amarelo,
  },
  noticiaData: {
    width: 74,
    textAlign: "right",
    fontSize: 11.5,
    color: cores.textoSuave,
  },
  secao: {
    marginTop: 24,
  },
  grade: {
    marginTop: 14,
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  acaoCard: {
    width: "47.8%",
    minHeight: 108,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: cores.borda,
    borderTopWidth: 4,
    borderTopColor: cores.primaria,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    paddingVertical: 16,
    paddingHorizontal: 8,
  },
  acaoLabel: {
    fontSize: 13.5,
    fontWeight: "600",
    color: cores.texto,
    textAlign: "center",
    lineHeight: 19,
  },
  emergenciaCard: {
    backgroundColor: cores.vermelhoClaro,
  },
  emergenciaLabel: {
    color: cores.vermelho,
  },
  pressionado: {
    opacity: 0.85,
  },
});
