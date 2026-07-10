import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useState } from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";

import AppHeader from "../components/AppHeader";
import { ApiError, criarDenuncia } from "../lib/api";
import type { RootStackParamList } from "../lib/navegacao";
import {
  CATEGORIAS,
  cores,
  hojeISO,
  type CategoriaDenuncia,
} from "../lib/theme";

type Props = NativeStackScreenProps<RootStackParamList, "NovaDenuncia">;

export default function NovaDenunciaScreen({ navigation, route }: Props) {
  const [categoria, setCategoria] = useState<CategoriaDenuncia | null>(
    route.params?.categoria ?? null,
  );
  const [titulo, setTitulo] = useState("");
  const [descricao, setDescricao] = useState("");
  const [endereco, setEndereco] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function enviar() {
    if (!categoria) {
      Alert.alert("Categoria obrigatória", "Selecione a categoria da denúncia.");
      return;
    }
    if (!titulo.trim() || !endereco.trim()) {
      Alert.alert(
        "Campos obrigatórios",
        "Preencha o título e o endereço da denúncia.",
      );
      return;
    }

    setEnviando(true);
    try {
      const denuncia = await criarDenuncia({
        titulo: titulo.trim(),
        descricao: descricao.trim() || null,
        endereco: endereco.trim(),
        categoria,
        data: hojeISO(),
      });
      Alert.alert(
        "Denúncia enviada!",
        `Sua denúncia foi registrada com o protocolo #${denuncia.id}. A prefeitura irá analisá-la em breve.`,
      );
      navigation.goBack();
    } catch (e) {
      Alert.alert(
        "Não foi possível enviar",
        e instanceof ApiError ? e.message : "Verifique sua conexão e tente novamente.",
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <View style={styles.tela}>
      <AppHeader titulo="Nova Denúncia" onVoltar={() => navigation.goBack()} />

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.conteudo}
          keyboardShouldPersistTaps="handled"
        >
          <Text style={styles.label}>Categoria</Text>
          <View style={styles.categorias}>
            {CATEGORIAS.map((item) => {
              const ativa = categoria === item.id;
              return (
                <Pressable
                  key={item.id}
                  accessibilityRole="button"
                  onPress={() => setCategoria(item.id)}
                  style={[styles.categoriaChip, ativa && styles.categoriaAtiva]}
                >
                  <Text
                    style={[
                      styles.categoriaChipTexto,
                      ativa && styles.categoriaAtivaTexto,
                    ]}
                  >
                    {item.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <Text style={styles.label}>Título</Text>
          <TextInput
            style={styles.input}
            placeholder="Ex.: Buraco na Rua das Flores"
            placeholderTextColor="#9CA3AF"
            value={titulo}
            onChangeText={setTitulo}
            maxLength={255}
          />

          <Text style={styles.label}>Descrição</Text>
          <TextInput
            style={[styles.input, styles.inputMultiline]}
            placeholder="Descreva o problema com detalhes"
            placeholderTextColor="#9CA3AF"
            value={descricao}
            onChangeText={setDescricao}
            multiline
            numberOfLines={4}
            textAlignVertical="top"
          />

          <Text style={styles.label}>Endereço</Text>
          <TextInput
            style={styles.input}
            placeholder="Ex.: Rua das Flores, 123"
            placeholderTextColor="#9CA3AF"
            value={endereco}
            onChangeText={setEndereco}
            maxLength={255}
          />

          <Pressable
            accessibilityRole="button"
            onPress={enviar}
            disabled={enviando}
            style={({ pressed }) => [
              styles.enviar,
              (pressed || enviando) && styles.pressionado,
            ]}
          >
            <Text style={styles.enviarTexto}>
              {enviando ? "Enviando..." : "Enviar Denúncia"}
            </Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  tela: {
    flex: 1,
    backgroundColor: cores.fundo,
  },
  flex: {
    flex: 1,
  },
  conteudo: {
    padding: 16,
    paddingBottom: 40,
  },
  label: {
    fontSize: 13.5,
    fontWeight: "700",
    color: cores.texto,
    marginBottom: 8,
    marginTop: 16,
  },
  categorias: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  categoriaChip: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 7,
  },
  categoriaAtiva: {
    backgroundColor: cores.primaria,
    borderColor: cores.primaria,
  },
  categoriaChipTexto: {
    fontSize: 13,
    fontWeight: "600",
    color: cores.texto,
  },
  categoriaAtivaTexto: {
    color: "#ffffff",
  },
  input: {
    borderRadius: 8,
    borderWidth: 1,
    borderColor: cores.borda,
    backgroundColor: "#ffffff",
    paddingHorizontal: 12,
    paddingVertical: 11,
    fontSize: 14,
    color: cores.texto,
    outlineWidth: 0,
  },
  inputMultiline: {
    minHeight: 96,
  },
  enviar: {
    marginTop: 28,
    height: 48,
    borderRadius: 8,
    backgroundColor: cores.primaria,
    alignItems: "center",
    justifyContent: "center",
  },
  enviarTexto: {
    fontSize: 15,
    fontWeight: "700",
    color: "#ffffff",
  },
  pressionado: {
    opacity: 0.85,
  },
});
