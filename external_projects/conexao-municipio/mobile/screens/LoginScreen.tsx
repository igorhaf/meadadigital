import { MaterialCommunityIcons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
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

import { validarCredenciais } from "../constants/auth";

interface LoginScreenProps {
  onLoginSuccess: () => void;
}

export default function LoginScreen({ onLoginSuccess }: LoginScreenProps) {
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  function entrar() {
    if (validarCredenciais(email, senha)) {
      setErro(null);
      onLoginSuccess();
      return;
    }

    const mensagem = "Credenciais inválidas. Use admin / admin.";
    setErro(mensagem);
    Alert.alert("Não foi possível entrar", mensagem);
  }

  return (
    <LinearGradient
      colors={["#3079B5", "#26A69A", "#4AAB4E"]}
      locations={[0, 0.5, 1]}
      style={styles.gradient}
    >
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          bounces={false}
        >
          <View style={styles.badge}>
            <MaterialCommunityIcons
              name="office-building"
              size={54}
              color="#ffffff"
            />
          </View>

          <Text style={styles.titulo}>Conexão Município</Text>
          <Text style={styles.subtitulo}>Sua cidade na palma da mão</Text>

          <View style={styles.form}>
            <View style={styles.campo}>
              <MaterialCommunityIcons name="email" size={20} color="#111827" />
              <TextInput
                style={styles.input}
                placeholder="E-mail"
                placeholderTextColor="#374151"
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="email-address"
                value={email}
                onChangeText={setEmail}
              />
            </View>

            <View style={[styles.campo, styles.campoSenha]}>
              <MaterialCommunityIcons name="lock" size={20} color="#111827" />
              <TextInput
                style={styles.input}
                placeholder="Senha"
                placeholderTextColor="#374151"
                autoCapitalize="none"
                autoCorrect={false}
                secureTextEntry={!mostrarSenha}
                value={senha}
                onChangeText={setSenha}
              />
              <Pressable
                onPress={() => setMostrarSenha((v) => !v)}
                hitSlop={10}
                accessibilityLabel={
                  mostrarSenha ? "Ocultar senha" : "Mostrar senha"
                }
              >
                <MaterialCommunityIcons
                  name={mostrarSenha ? "eye-off" : "eye"}
                  size={22}
                  color="#111827"
                />
              </Pressable>
            </View>
          </View>

          {erro ? <Text style={styles.erro}>{erro}</Text> : null}

          <Pressable
            hitSlop={8}
            accessibilityRole="button"
            style={styles.esqueceuWrapper}
          >
            <Text style={styles.esqueceu}>Esqueceu a senha?</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.botaoEntrar,
              pressed && styles.pressionado,
            ]}
            onPress={entrar}
            accessibilityRole="button"
          >
            <MaterialCommunityIcons name="login" size={20} color="#ffffff" />
            <Text style={styles.botaoEntrarTexto}>ENTRAR</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.botaoCriarConta,
              pressed && styles.pressionado,
            ]}
            accessibilityRole="button"
          >
            <MaterialCommunityIcons
              name="account-plus"
              size={20}
              color="#ffffff"
            />
            <Text style={styles.botaoCriarContaTexto}>Criar conta</Text>
          </Pressable>

          <View style={styles.flex} />
          <Text style={styles.versao}>Versão 1.0.0</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  gradient: {
    flex: 1,
  },
  flex: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    alignItems: "center",
    paddingHorizontal: 26,
    paddingTop: 96,
    paddingBottom: 96,
  },
  badge: {
    width: 112,
    height: 112,
    borderRadius: 56,
    backgroundColor: "rgba(255, 255, 255, 0.18)",
    alignItems: "center",
    justifyContent: "center",
  },
  titulo: {
    marginTop: 30,
    fontSize: 30,
    fontWeight: "800",
    color: "#ffffff",
  },
  subtitulo: {
    marginTop: 8,
    fontSize: 15,
    fontWeight: "600",
    color: "#ffffff",
  },
  form: {
    width: "100%",
    marginTop: 42,
    gap: 16,
  },
  campo: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    width: "100%",
    height: 56,
    borderRadius: 12,
    paddingHorizontal: 16,
    backgroundColor: "#DCEBE4",
    borderWidth: 1,
    borderColor: "rgba(17, 24, 39, 0.15)",
  },
  campoSenha: {
    borderWidth: 2,
    borderColor: "rgba(17, 24, 39, 0.7)",
  },
  input: {
    flex: 1,
    height: "100%",
    fontSize: 15,
    color: "#111827",
    outlineWidth: 0,
  },
  erro: {
    marginTop: 14,
    fontSize: 13,
    fontWeight: "600",
    color: "#FFE082",
  },
  esqueceuWrapper: {
    alignSelf: "flex-end",
    marginTop: 18,
  },
  esqueceu: {
    fontSize: 15,
    fontWeight: "700",
    color: "#ffffff",
  },
  botaoEntrar: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    width: "100%",
    height: 54,
    marginTop: 42,
    borderRadius: 10,
    backgroundColor: "#2196F3",
  },
  botaoEntrarTexto: {
    fontSize: 16,
    fontWeight: "800",
    letterSpacing: 1.5,
    color: "#ffffff",
  },
  botaoCriarConta: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    width: "100%",
    height: 54,
    marginTop: 18,
    borderRadius: 10,
    borderWidth: 1.5,
    borderColor: "#ffffff",
  },
  botaoCriarContaTexto: {
    fontSize: 16,
    fontWeight: "600",
    color: "#ffffff",
  },
  pressionado: {
    opacity: 0.85,
  },
  versao: {
    marginTop: 56,
    fontSize: 12,
    color: "rgba(255, 255, 255, 0.75)",
  },
});
