import type { PetAppointmentStatusId } from './pet-appointment-status'

/** Profissional do pet shop (espelha PetProfessional). */
export type PetProfessional = {
  id: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Serviço do pet shop (espelha PetService). priceCents/speciesRestriction nullable. */
export type PetService = {
  id: string
  name: string
  category: string | null
  durationMinutes: number
  priceCents: number | null
  speciesRestriction: PetSpecies | null
  active: boolean
  description: string | null
  createdAt: string
  updatedAt: string
}

/** Config do pet shop (espelha PetConfig). opensAt/closesAt em "HH:MM:SS". */
export type PetConfig = {
  companyId: string
  opensAt: string
  closesAt: string
  bufferMinutes: number
}

/** Espécie do animal (espelha o CHECK de pet_animals.species). */
export type PetSpecies = 'cao' | 'gato' | 'outro'
/** Sexo do animal (espelha o CHECK de pet_animals.sex). */
export type PetSex = 'macho' | 'femea' | 'desconhecido'

/** Animal — sub-entidade do tutor (espelha PetAnimal). */
export type PetAnimal = {
  id: string
  contactId: string
  name: string
  species: PetSpecies
  breed: string | null
  sex: PetSex | null
  birthYear: number | null
  notes: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

/** Agendamento (espelha PetAppointment). startAt/endAt em ISO-8601 instant. */
export type PetAppointment = {
  id: string
  professionalId: string
  professionalName: string
  serviceId: string
  serviceName: string
  serviceCategory: string | null
  animalId: string
  animalName: string
  animalSpecies: PetSpecies
  contactId: string | null
  conversationId: string | null
  tutorName: string
  tutorPhone: string | null
  priceCents: number | null
  durationMinutes: number
  startAt: string
  endAt: string
  status: PetAppointmentStatusId
  notes: string | null
  createdAt: string
  statusUpdatedAt: string
}

/** Detalhe do conflito no 409 conflict_slot (por profissional). */
export type ConflictDetail = {
  appointmentId: string
  animalName: string
  tutorName: string
  startAt: string
  endAt: string
}

const SPECIES_LABELS: Record<PetSpecies, string> = {
  cao: 'Cão',
  gato: 'Gato',
  outro: 'Outro',
}
export function speciesLabel(s: string | null | undefined): string {
  if (!s) return '—'
  return SPECIES_LABELS[s as PetSpecies] ?? s
}

const SEX_LABELS: Record<PetSex, string> = {
  macho: 'Macho',
  femea: 'Fêmea',
  desconhecido: 'Desconhecido',
}
export function sexLabel(s: string | null | undefined): string {
  if (!s) return '—'
  return SEX_LABELS[s as PetSex] ?? s
}

/** Formata centavos em R$ pt-BR (— se null). */
export function formatPrice(cents: number | null): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR')
}
