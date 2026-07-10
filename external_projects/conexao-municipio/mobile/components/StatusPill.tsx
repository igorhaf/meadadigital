import { StyleSheet, Text, View } from "react-native";

import { STATUS_INFO, type StatusDenuncia } from "../lib/theme";

export default function StatusPill({ status }: { status: StatusDenuncia }) {
  const info = STATUS_INFO[status];

  return (
    <View style={[styles.pill, { backgroundColor: info.cor }]}>
      <Text style={styles.texto}>{info.label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  texto: {
    fontSize: 10,
    fontWeight: "800",
    color: "#ffffff",
    letterSpacing: 0.3,
  },
});
