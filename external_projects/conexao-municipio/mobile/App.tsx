import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { StatusBar } from "expo-status-bar";
import { useState } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";

import type { RootStackParamList } from "./lib/navegacao";
import AgendarScreen from "./screens/AgendarScreen";
import DenunciasScreen from "./screens/DenunciasScreen";
import HomeScreen from "./screens/HomeScreen";
import LoginScreen from "./screens/LoginScreen";
import NovaDenunciaScreen from "./screens/NovaDenunciaScreen";

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  const [logado, setLogado] = useState(false);

  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      {logado ? (
        <NavigationContainer>
          <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Home">
              {(props) => (
                <HomeScreen {...props} onLogout={() => setLogado(false)} />
              )}
            </Stack.Screen>
            <Stack.Screen name="Denuncias" component={DenunciasScreen} />
            <Stack.Screen name="NovaDenuncia" component={NovaDenunciaScreen} />
            <Stack.Screen name="Agendar" component={AgendarScreen} />
          </Stack.Navigator>
        </NavigationContainer>
      ) : (
        <LoginScreen onLoginSuccess={() => setLogado(true)} />
      )}
    </SafeAreaProvider>
  );
}
