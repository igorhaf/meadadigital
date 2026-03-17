import { NextRequest } from 'next/server'

export async function POST(req: NextRequest) {
  const { message, session_id } = await req.json()

  const claudioBase = (process.env.CLAUDE_ADDRESS ?? 'http://claudio.local/v1/messages')
    .replace('/v1/messages', '')

  const upstream = await fetch(`${claudioBase}/api/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': '123456789',
    },
    body: JSON.stringify({ message, session_id }),
  })

  if (!upstream.ok) {
    return new Response(JSON.stringify({ error: 'upstream error' }), { status: 502 })
  }

  return new Response(upstream.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  })
}
