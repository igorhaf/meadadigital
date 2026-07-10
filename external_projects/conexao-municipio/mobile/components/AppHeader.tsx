import { MaterialCommunityIcons } from "@expo/vector-icons";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { cores } from "../lib/theme";

interface AppHeaderProps {
  titulo: string;
  onVoltar: () => void;
}

export default function AppHeader({ titulo, onVoltar }: AppHeaderProps) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.barra, { paddingTop: insets.top + 10 }]}>
      <Pressable
        onPress={onVoltar}
        accessibilityRole="button"
        accessibilityLabel="Voltar"
        style={styles.voltar}
      >
        <MaterialCommunityIcons
          name="arrow-left"
          size={20}
          color={cores.primaria}
        />
      </Pressable>
      <Text style={styles.titulo}>{titulo}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  barra: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    backgroundColor: cores.primaria,
    paddingHorizontal: 14,
    paddingBottom: 12,
  },
  voltar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
  },
  titulo: {
    fontSize: 17,
    fontWeight: "700",
    color: "#ffffff",
  },
});
